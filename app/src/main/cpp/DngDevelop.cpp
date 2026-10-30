#include "DngDevelop.h"

#include <errno.h>
#include <unistd.h>

#include <opencv2/core.hpp>
#include <sstream>
#include <string>

#include <android/log.h>
#include <libraw/libraw.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "DngDevelop", __VA_ARGS__)

namespace {

class FdDataStream : public LibRaw_abstract_datastream {
public:
    explicit FdDataStream(int fd) : fd_(fd), pos_(0), size_(0) {
        const off_t end = ::lseek(fd_, 0, SEEK_END);
        if (end >= 0) size_ = static_cast<INT64>(end);
        if (::lseek(fd_, 0, SEEK_SET) >= 0) pos_ = 0;
    }
    int valid() override { return fd_ >= 0 && size_ > 0; }
    int read(void* ptr, size_t size, size_t nmemb) override {
        if (size == 0) return 0;
        const size_t want = size * nmemb;
        auto* out = static_cast<unsigned char*>(ptr);
        size_t got = 0;
        while (got < want) {
            const ssize_t n = ::read(fd_, out + got, want - got);
            if (n < 0) {
                if (errno == EINTR) continue;
                return 0;
            }
            if (n == 0) break;
            got += static_cast<size_t>(n);
            pos_ += n;
        }
        return static_cast<int>(got / size);
    }
    int seek(INT64 o, int whence) override {
        const off_t n = ::lseek(fd_, static_cast<off_t>(o), whence);
        if (n < 0) return -1;
        pos_ = static_cast<INT64>(n);
        return 0;
    }
    INT64 tell() override { return pos_; }
    INT64 size() override { return size_; }
    int get_char() override {
        unsigned char c = 0;
        if (read(&c, 1, 1) != 1) return -1;
        return c;
    }
    char* gets(char* str, int sz) override {
        if (!str || sz < 2) return nullptr;
        int i = 0;
        while (i < sz - 1) {
            const int c = get_char();
            if (c < 0) break;
            str[i++] = static_cast<char>(c);
            if (c == '\n') break;
        }
        if (i == 0) return nullptr;
        str[i] = 0;
        return str;
    }
    int scanf_one(const char* fmt, void* val) override {
        char line[128];
        const INT64 save = tell();
        if (!gets(line, static_cast<int>(sizeof(line)))) return EOF;
        if (sscanf(line, fmt, val) == 1) return 1;
        seek(save, SEEK_SET);
        return EOF;
    }
    int eof() override { return pos_ >= size_; }
    const char* fname() override { return "fd"; }

private:
    int fd_;
    INT64 pos_;
    INT64 size_;
};

void setErr(std::string* err, const std::string& msg) {
    if (err) *err = msg;
}

std::string withDims(const char* why, int code, int w, int h) {
    std::ostringstream os;
    os << why << " code=" << code << " " << libraw_strerror(code) << " " << w << "x" << h;
    return os.str();
}

}  // namespace

bool veDngDevelopRgb(int fd, cv::Mat* outRgb, std::string* err) {
    if (!outRgb) {
        setErr(err, "veDngDevelopRgb: null out");
        return false;
    }
    const int local = ::dup(fd);
    if (local < 0) {
        setErr(err, "veDngDevelopRgb: dup failed");
        return false;
    }
    try {
        FdDataStream stream(local);
        if (!stream.valid()) {
            ::close(local);
            setErr(err, "veDngDevelopRgb: empty fd");
            return false;
        }
        LibRaw raw;
        int ret = raw.open_datastream(&stream);
        const int rawW = raw.imgdata.sizes.raw_width;
        const int rawH = raw.imgdata.sizes.raw_height;
        const int iW = raw.imgdata.sizes.iwidth;
        const int iH = raw.imgdata.sizes.iheight;
        const int flip = raw.imgdata.sizes.flip;
        LOGI("header raw=%dx%d i=%dx%d flip=%d open=%d", rawW, rawH, iW, iH, flip, ret);
        if (ret != LIBRAW_SUCCESS) {
            raw.recycle();
            ::close(local);
            setErr(err, withDims("veDngDevelopRgb: open_datastream", ret, rawW, rawH));
            return false;
        }
        ret = raw.unpack();
        if (ret != LIBRAW_SUCCESS) {
            raw.recycle();
            ::close(local);
            setErr(err, withDims("veDngDevelopRgb: unpack", ret, iW, iH));
            return false;
        }
        raw.imgdata.params.output_color = 1;
        raw.imgdata.params.use_camera_wb = 1;
        raw.imgdata.params.user_qual = 0;
        raw.imgdata.params.half_size = 0;
        ret = raw.dcraw_process();
        if (ret != LIBRAW_SUCCESS) {
            raw.recycle();
            ::close(local);
            setErr(err, withDims("veDngDevelopRgb: dcraw_process", ret, iW, iH));
            return false;
        }
        libraw_processed_image_t* image = raw.dcraw_make_mem_image(&ret);
        if (!image || ret != LIBRAW_SUCCESS) {
            if (image) LibRaw::dcraw_clear_mem(image);
            raw.recycle();
            ::close(local);
            setErr(err, withDims("veDngDevelopRgb: dcraw_make_mem_image", ret, iW, iH));
            return false;
        }
        if (image->colors != 3 || image->bits != 8 || image->width < 1 || image->height < 1) {
            const int w = image->width;
            const int h = image->height;
            LibRaw::dcraw_clear_mem(image);
            raw.recycle();
            ::close(local);
            setErr(err, withDims("veDngDevelopRgb: unexpected image", ret, w, h));
            return false;
        }
        cv::Mat view(image->height, image->width, CV_8UC3, image->data);
        view.copyTo(*outRgb);
        LibRaw::dcraw_clear_mem(image);
        raw.recycle();
        ::close(local);
        if (outRgb->empty() || outRgb->type() != CV_8UC3) {
            setErr(err, "veDngDevelopRgb: empty rgb");
            return false;
        }
        return true;
    } catch (const std::bad_alloc&) {
        ::close(local);
        setErr(err, "veDngDevelopRgb: bad_alloc");
        return false;
    } catch (const cv::Exception& ex) {
        ::close(local);
        setErr(err, std::string("veDngDevelopRgb: ") + ex.what());
        return false;
    } catch (const std::exception& ex) {
        ::close(local);
        setErr(err, std::string("veDngDevelopRgb: ") + ex.what());
        return false;
    }
}
