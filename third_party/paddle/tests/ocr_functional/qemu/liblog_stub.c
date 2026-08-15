/* Minimal Android liblog for qemu-user paddle harness (no device liblog/libc++). */
#include <stddef.h>
#include <stdarg.h>

int __android_log_write(int prio, const char* tag, const char* text) {
  (void)prio;
  (void)tag;
  (void)text;
  return 0;
}
int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
  (void)prio;
  (void)tag;
  (void)fmt;
  return 0;
}
int __android_log_vprint(int prio, const char* tag, const char* fmt, va_list ap) {
  (void)prio;
  (void)tag;
  (void)fmt;
  (void)ap;
  return 0;
}
int __android_log_buf_write(int bufID, int prio, const char* tag, const char* text) {
  (void)bufID;
  (void)prio;
  (void)tag;
  (void)text;
  return 0;
}
int __android_log_buf_print(int bufID, int prio, const char* tag, const char* fmt, ...) {
  (void)bufID;
  (void)prio;
  (void)tag;
  (void)fmt;
  return 0;
}
void __android_log_assert(const char* cond, const char* tag, const char* fmt, ...) {
  (void)cond;
  (void)tag;
  (void)fmt;
}
int __android_log_is_loggable(int prio, const char* tag, int def) {
  (void)prio;
  (void)tag;
  (void)def;
  return 1;
}
int __android_log_is_loggable_len(int prio, const char* tag, size_t len, int def) {
  (void)prio;
  (void)tag;
  (void)len;
  (void)def;
  return 1;
}
