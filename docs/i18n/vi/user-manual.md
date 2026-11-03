# <a id="vehicle-expenses-automated--user-manual"></a>Chi phí phương tiện tự động — Hướng dẫn sử dụng

> **Chỉnh sửa nguồn (Markdown).** Trình duyệt và trình đọc trong ứng dụng mở **HTML được kết xuất**:
> - Web: [`docs/user-manual/index.html`](index.html) (tái tạo bằng `./scripts/render-user-manual.sh`)
> - Ứng dụng: Trợ giúp / Giới thiệu → hướng dẫn đầy đủ (HTML + ảnh chụp màn hình đi kèm)
>
> Không trỏ người dùng cuối tới các URL `.md` thô — trình duyệt chỉ hiển thị văn bản thuần túy.

Camera đầu tiên theo dõi việc đổ xăng và chi phí xe, với tính năng đồng bộ hóa và sao lưu đa thiết bị tùy chọn trong tài khoản đám mây ** của bạn **.

Đây là **hướng dẫn đầy đủ** (ảnh chụp màn hình + từng bước). Trên điện thoại, **Menu → Trợ giúp** là hướng dẫn bắt đầu ngắn hơn.

**Không được đề cập ở đây:** Nhập Ảnh cũ, Thử nghiệm căn chỉnh và Thử nghiệm bơm (nhà phát triển / công cụ nâng cao).

---

## <a id="table-of-contents"></a>Mục lục

1. [Thứ bạn cần](#what-you-need)
2. [Tóm tắt các biểu tượng](#icons-at-a-glance)
3. [Mở menu](#open-the-menu)
4. [Thiết lập lần đầu: Quản lý phương tiện](#first-time-setup-manage-vehicles)
5. [Sao lưu và đồng bộ hóa đa thiết bị](#backups-and-multi-device-sync)
6. [Đổ (nhiên liệu)](#fill-up-fuel)
7. [Bắt đầu chuyến đi](#start-trip)
8. [Chi phí](#expenses)
9. [Báo cáo](#reports)
10. [Cài đặt (tùy chọn cục bộ)](#settings-local-preferences)
11. [Đang đồng bộ hóa](#syncing)
12. [Trợ giúp & Giới thiệu](#help--about)
13. [Tài liệu liên quan](#related-docs)

---

## <a id="what-you-need"></a>Những gì bạn cần

- Điện thoại hoặc máy tính bảng Android.
- Để có OCR tốt nhất: chế độ xem rõ ràng **đồng hồ đo đường trên bảng điều khiển** và **tổng số máy bơm** (hoặc nhập số bằng tay).
- Tùy chọn: các tài khoản **bạn kiểm soát** để sao lưu dữ liệu bảng tính và/hoặc sao lưu ảnh (xem [Sao lưu và đồng bộ hóa đa thiết bị](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Tổng quan về các biểu tượng

Chúng xuất hiện trên màn hình chính. Biết chúng giúp tiết kiệm rất nhiều cuộc săn bắn.

| Ở đâu | Biểu tượng / điều khiển | Nó làm gì |
|-------|-------|--------------|
| Thanh trên cùng | **☰ Thực đơn** (hamburger) | Mở ngăn điều hướng |
| Thanh trên cùng | **ⓘ** (trang trợ giúp) | Trợ giúp ngắn gọn cho trang **hiện tại** (bên cạnh menu nếu có) |
| Thanh trên cùng | **`?N`** (màu vàng) | Các câu hỏi đánh giá nhập đang chờ xử lý — mở Đánh giá nhập |
| Thanh trên cùng | ***!** (đỏ) | Gần đây, đích đến của bảng tính hoặc ảnh không thành công — mở **Đang đồng bộ hóa** để khắc phục |
| Thanh trên cùng | **☰ + ←** | Báo cáo con và danh sách Chi phí hiển thị **menu và quay lại** cùng nhau; Trung tâm báo cáo chỉ có trong menu |
| Cài đặt / chỉnh sửa nhiên liệu | **←** | Quay lại (bảng tính cài đặt/chỉnh sửa ảnh và nhiên liệu vẫn tập trung trở lại) |
| Điền | **Vòng tròn màu trắng** (màn trập) | Chụp màn hình đồng hồ đo đường hoặc máy bơm cho OCR |
| Điền | **Đĩa / Lưu** | Tiết kiệm nhiên liệu (cần một chiếc xe và ít nhất một trong số odo/khối lượng/chi phí) |
| Điền | **↕ mũi tên** (chuyển đổi chế độ) | Chuyển đổi giữa **chế độ đo đường** và **chế độ bơm (chi phí/khối lượng)**. Đường viền màu xanh lá cây làm nổi bật nhóm trường đang hoạt động |
| Điền | **↔ mũi tên** (giữa chi phí và khối lượng) | Hoán đổi chi phí và khối lượng nếu OCR đặt chúng vào sai trường |
| Điền | **Thu phóng 1x / …** | Tỷ lệ thu phóng của máy ảnh khi ống kính hỗ trợ chúng |
| Điền (sau khi chụp) | **Làm mới** trên nút chính | Loại bỏ bản xem trước và quay lại camera trực tiếp |
| Điền (trong khi xử lý) | **X** trên nút chính | Hủy chụp/OCR đang thực hiện |
| Chi phí | **Lưu** | Tiết kiệm chi phí |
| Chi phí | **Vòng màn trập** | Chụp ảnh biên nhận |
| Chi phí | **Thư viện** | Chọn hình ảnh biên nhận từ thư viện |
| Chi phí | **Thi lại** | Xóa ảnh biên lai hiện tại và chụp lại |
| Chi phí / Quản lý phương tiện | *** / −** FAB | Thu phóng bản xem trước ảnh |
| Hộp thoại cột mốc | **Chỉnh sửa OCR** ​​| Sửa hoặc thêm văn bản mốc mà công cụ bỏ sót |
| Bảng tính / Biểu mẫu ảnh | **🔍 Tìm kiếm** | Duyệt qua Google Drive để tìm trang tính hoặc thư mục (sau khi đăng nhập) |

Bạn có thể nhấn vào các ký hiệu tiền tệ trên trường chi phí và **G/L** trên trường âm lượng: mở một menu nhỏ để thay đổi đơn vị tiền tệ hoặc gallon so với lít cho mục nhập đó.

---

## <a id="open-the-menu"></a>Mở menu

1. Nhấn vào **☰** ở trên cùng bên trái.
2. Chọn một trang.

![Ngăn điều hướng](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Ngăn kéo chính:** Điền · Bắt đầu chuyến đi · Quản lý phương tiện · Chi phí mới · **Báo cáo** · Cài đặt · Đồng bộ hóa · Trợ giúp · Giới thiệu.

**Ngăn kéo thử nghiệm** (Cài đặt → Hiển thị màn hình thử nghiệm): Thử nghiệm căn chỉnh · Thử nghiệm bơm · **Nhập ảnh cũ**.

**Thông qua trung tâm Báo cáo (không phải ngăn kéo chính):** Danh sách chi phí · Điền vào lịch sử.

---

## <a id="first-time-setup-manage-vehicles"></a>Thiết lập lần đầu: Quản lý phương tiện

OCR và **khớp xe tự động** hoạt động tốt nhất sau khi bạn đăng ký mỗi chiếc xe với một **ảnh bảng điều khiển tham chiếu**, chạy **Discovery**, đặt phần cắt đồng hồ đo đường từ một hạt mái chèo và lưu các mốc cho dấu gạch ngang đó.

### <a id="open-manage-vehicles"></a>Mở Quản lý phương tiện

Menu → **Quản lý phương tiện**. Chọn một phương tiện (hoặc **Thêm phương tiện mới**).

![Quản lý phương tiện với dấu gạch ngang, cây trồng và khám phá tham chiếu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Thêm hoặc chỉnh sửa xe

1. Mở menu thả xuống **Xe** → chọn một phương tiện hoặc **Thêm phương tiện mới**.
2. Chụp hoặc chọn một **ảnh gạch ngang tham chiếu** rõ ràng (cụm thiết bị đầy đủ, đủ ánh sáng, điện thoại gần như vuông vắn). Sử dụng **Chụp ảnh** hoặc **Thư viện**.
3. Nhấn vào **Chạy khám phá** — tính năng phát hiện mái chèo đặt **hạt** màu vàng trên ảnh (các mốc sử dụng cùng một `"ML Kit"` JSON như trước).
4. Nhấn **Chọn Odo**, nhấn vào hạt giống trên các chữ số, điều chỉnh tay cầm, sau đó **Lưu**.
5. **Chỉnh sửa Odo** điều chỉnh hộp đã có sẵn. **Clear Odo** loại bỏ nó và nút sau đó sẽ ghi **Chọn Odo**.
6. Đánh giá bằng **Hiển thị các cột mốc**. Sử dụng **Chỉnh sửa OCR** ​​để sửa lỗi đọc sai hoặc **thêm** văn bản bị bỏ sót.
7. Điền **Tên xe** (bắt buộc), cộng với nhãn hiệu/model/năm/biển số tùy thích.
8. Nhấn vào **Tạo phương tiện** hoặc **Lưu thay đổi** (yêu cầu tên + ảnh tham chiếu cho phương tiện mới).

![Thêm trường mẫu xe mới](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Mốc: sửa những gì Discovery đã bỏ lỡ

Sau **Hiển thị cột mốc**, hãy cuộn danh sách và sửa các giá trị. Động cơ đôi khi bỏ lỡ các chữ số nhỏ (ví dụ: đồng hồ **60** ở phía dưới bên phải của cụm). Sử dụng **Chỉnh sửa OCR** ​​để thêm hoặc sửa chúng để nhận dạng phương tiện luôn đáng tin cậy.

![Hộp thoại cột mốc — xem xét và chỉnh sửa OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Đánh máy mà không có bức ảnh hoàn hảo

Bạn vẫn có thể sử dụng ứng dụng bằng cách chọn một chiếc xe và **nhập** đồng hồ đo quãng đường, khối lượng và chi phí trên Điền — OCR là tùy chọn cho mọi trường. Tính năng nhập thư viện hoạt động đối với ảnh gạch ngang tham chiếu khi bạn không muốn chụp trong ứng dụng.

**Mẹo:** Sau khi đồng bộ hóa bảng tính, các định nghĩa về phương tiện (cây trồng, cột mốc) sẽ có trong cơ sở dữ liệu cục bộ — bạn không cần phải mở lại Quản lý phương tiện để Điền để sử dụng chúng.

---

## <a id="backups-and-multi-device-sync"></a>Sao lưu và đồng bộ hóa đa thiết bị

Ứng dụng được xây dựng để **một số điện thoại hoặc máy tính bảng có thể chia sẻ cùng một dữ liệu nhóm** và vì vậy bạn có thể giữ **bản sao dữ liệu và ảnh của mình khỏi thiết bị**. Điều đó được thực hiện với các điểm đến **bạn** định cấu hình trong tài khoản **của bạn** hoặc máy chủ tự lưu trữ **của bạn** — không phải là “Đám mây chi phí phương tiện” do công ty điều hành mà người khác có thể nhìn thấy.

### <a id="what-runs-where"></a>Cái gì chạy đâu

| Loại | Nó lưu trữ những gì | Sử dụng điển hình |
|------|-------|-------------|
| **Đồng bộ hóa bảng tính/dạng bảng** | Xe cộ, đổ xăng, chi phí (hàng và tab) | Hợp nhất nhiều thiết bị + sao lưu có cấu trúc |
| **Sao lưu ảnh** | Hình ảnh nhị phân (dấu gạch ngang/bơm/biên nhận/ảnh tham khảo) | Sao lưu ảnh + khôi phục file bị thiếu |

Bạn có thể định cấu hình **nhiều đích** của từng loại (giới hạn mềm cho mỗi loại). Nhân viên **Đồng bộ hóa ngay** và **nền** thủ công chạy những cái đã bật.

### <a id="offline-first"></a>Ngoại tuyến trước

- **Không cần mạng** để thêm khoản điền vào, chi phí hoặc biên lai. Mọi thứ đều được lưu **cục bộ trước tiên**.
- Khi có mạng, đồng bộ hóa và sao lưu ảnh sẽ chạy dưới dạng **tác vụ nền** (theo lịch bạn đặt và khi bạn nhấn vào **Đồng bộ hóa ngay**). Lỗi hiển thị dưới dạng văn bản màu đỏ trong hàng Cài đặt và **!** trên thanh tiêu đề ứng dụng.

### <a id="your-accounts-only"></a>Chỉ tài khoản của bạn

Đăng nhập và mã thông báo vẫn còn trên thiết bị cho các nhà cung cấp bạn chọn (Google, Microsoft, khóa S3, URL tự lưu trữ, v.v.). Các điểm đến nằm dưới **toàn quyền kiểm soát của người dùng** — tài khoản Google, OneDrive, bộ chứa MinIO, máy chủ EtherCalc của bạn, v.v. Không có gì được chia sẻ với những người dùng Chi phí phương tiện khác thông qua chương trình phụ trợ được chia sẻ.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Mục tiêu được hỗ trợ — dữ liệu (bảng tính / dạng bảng)

Được định cấu hình trong **Menu → Đồng bộ hóa → Đồng bộ hóa bảng tính** (cũng có thể truy cập được từ các hàng tóm tắt Cài đặt). Tùy chọn bộ chọn hạng nhất:

| Mục tiêu | Ghi chú |
|--------|--------|
| **Google Trang tính** | Mặc định chung; các tab dành cho Phương tiện, Chi phí và nhiên liệu trên mỗi phương tiện |
| **Excel** | Sổ làm việc của Microsoft thông qua liên kết kiểu Đồ thị / OneDrive |
| **EtherCalc** | Phòng bảng tính cộng tác tự lưu trữ |
| **Khác →** phụ trợ được triển khai | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Trì hoãn/chưa headless (được liệt kê trong phần Khác nhưng chưa được triển khai đầy đủ): OnlyOffice, Collabora. Xem thêm [chỉ số tự lưu trữ](reference/self-host/INDEX.md).

CSV **xuất/nhập** (ZIP có cùng bố cục tab) có sẵn trong Cài đặt dưới dạng bản sao lưu di động, độc lập với đồng bộ hóa trực tiếp.

### <a id="supported-targets--photos-image-backup"></a>Mục tiêu được hỗ trợ — ảnh (sao lưu ảnh)

Được định cấu hình trong **Menu → Đồng bộ hóa → Sao lưu ảnh** (cũng từ các hàng tóm tắt Cài đặt):

| Mục tiêu | Ghi chú |
|--------|--------|
| **Google Drive** | Thư mục bạn chọn (duyệt hoặc dán URL) |
| **OneDrive** | Tài khoản Microsoft + tiền tố đường dẫn |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO và các điểm cuối tương thích với S3 khác |
| **Khác** | bộ lưu trữ được hỗ trợ bởi rclone (ví dụ: WebDAV, SFTP và các điều khiển từ xa được quản lý khác có sẵn trong bộ chọn trong ứng dụng) |

Thiết lập bảng tính cho các mục tiêu dạng bảng và ảnh tự lưu trữ: [chỉ mục tự lưu trữ](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Hành vi đa thiết bị (ngắn)

- Các hàng hợp nhất theo **ID đồng bộ hóa** với **lượt ghi cuối cùng** trên dấu thời gian **Đã cập nhật**.
- Xóa nhẹ nhàng; chỉnh sửa mới hơn trên thiết bị khác có thể khôi phục một hàng.
- Nhập **cùng một lần điền hai lần** trên hai thiết bị sẽ tạo ra **hai hàng** — hãy xóa phần bổ sung khi bạn nhận thấy.
- Chi tiết hơn: [Ghi chú hành vi đồng bộ hóa](#sync-behavior-notes) và [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Ví dụ: thêm Google Trang tính (dữ liệu)

1. **Menu → Đồng bộ hóa → Đồng bộ hóa bảng tính** (hoặc Cài đặt → Đồng bộ hóa bảng tính).

![Danh sách đồng bộ bảng tính](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Nhấn **Thêm đích bảng tính**.

![Bộ chọn nhà cung cấp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Chọn **Google Trang tính**.

![Biểu mẫu Google Trang tính](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Đăng nhập bằng Google** một lần — đăng nhập đó cũng cho phép bạn duyệt web. Tên hiển thị → dán **URL trang tính** (vẫn hợp lệ) hoặc **🔍** duyệt. Duyệt mở trên **Tất cả & được chia sẻ** để bạn có thể chọn bảng tính hiện có mà bạn sở hữu hoặc bảng tính được chia sẻ với bạn. Sau đó lên lịch tùy chọn → bật → lưu.
5. **Đồng bộ hóa ngay** một lần để tạo/cập nhật các tab: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Ví dụ: thêm Google Drive (ảnh)

1. **Menu → Đồng bộ hóa → Sao lưu ảnh** (hoặc Cài đặt → Sao lưu ảnh).

![Danh sách sao lưu ảnh](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Nhấn vào **Thêm điểm đến ảnh**.

![Bộ chọn nhà cung cấp ảnh](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Chọn **Google Drive**.

![Biểu mẫu Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Đăng nhập bằng Google (Drive)** → URL thư mục tùy chọn/duyệt → bật → lưu → **Đồng bộ hóa ngay**.

Thủ công **Đồng bộ hóa ngay** cho ảnh là hoàn thành; sao lưu nền thường xử lý các tải lên **chỉ đang chờ xử lý** theo lịch trình.

### <a id="sync-behavior-notes"></a> Ghi chú hành vi đồng bộ hóa

- Sau khi nâng cấp ứng dụng, bạn có thể thấy nhanh **“Đang cập nhật cơ sở dữ liệu sau khi nâng cấp…”** (chèn lấp id đồng bộ hóa cục bộ).
- Nếu quá trình đồng bộ hóa bị gián đoạn, lần đồng bộ hóa **thành công** tiếp theo sẽ hợp nhất lại và sửa chữa các tab từ xa.
- Lỗi: tóm tắt màu đỏ về Thẻ đồng bộ hóa + **!** trên thanh ứng dụng.

---

## <a id="fill-up-fuel"></a>Đổ đầy (nhiên liệu)

Đây là **màn hình chính** khi bạn mở ứng dụng.

### <a id="vehicle-selection-usually-automatic"></a>Chọn xe (thường là số tự động)

Bạn **không** cần phải chọn xe trước. Khi xe có **mốc** được thiết lập trong Quản lý phương tiện, Điền **tự động phát hiện phương tiện nào** từ hình ảnh gạch ngang sau khi bạn chụp đồng hồ đo đường. Bạn vẫn có thể mở menu thả xuống **Xe** để ghi đè nếu cần.

### <a id="aim-at-the-odometer"></a>Nhắm vào đồng hồ đo đường

Ở chế độ đo đường và đóng khung cụm. Hướng dẫn: *Nhắm vào đồng hồ đo đường. Nhấn vào màn trập để chụp.*

![Điền — camera trực tiếp trên đồng hồ đo đường (ví dụ hình ảnh gạch ngang trong bản xem trước)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Sau màn trập đồng hồ đo đường

OCR lấp đầy **Odo** và cố gắng khớp xe từ các mốc (xem lại cả hai nếu cần). Nút chính trở thành **Thử lại** để chụp lại. Hướng dẫn tóm tắt bài đọc.

![Điền — kết quả OCR của đồng hồ đo đường](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Chế độ bơm (chi phí và khối lượng)

1. Nhấn **↕** để chuyển sang chế độ bơm: *Nhắm vào màn hình bơm (chi phí/khối lượng). Nhấn vào màn trập.*
2. Ghi lại tổng số máy bơm. Các trường chi phí và khối lượng được điền vào; sử dụng **↔** nếu chúng được đổi chỗ.
3. Nhấn vào tiền tệ hoặc **G/L** nếu cần, sau đó **Save** (đĩa). Các trường trống sẽ **điền một phần** (vẫn được phép).

![Điền — xem trước máy bơm / tổng số (ví dụ hình ảnh máy bơm trong bản xem trước)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Bạn tiếp tục Điền cho điểm dừng tiếp theo (các trường sẽ bị xóa sau khi lưu). Làm việc hoàn toàn **ngoại tuyến**; đồng bộ hóa sẽ chạy sau trong nền khi được định cấu hình.

### <a id="manual-entry-no-camera--bad-ocr"></a>Nhập thủ công (không có camera / OCR kém)

1. Nhấn vào **Odo**, **chi phí** hoặc **âm lượng** và nhập các giá trị (dọc sử dụng bàn phím hệ thống; ngang sử dụng bàn phím trên màn hình).
2. Chọn hoặc xác nhận **Xe** nếu tính năng tự động phát hiện không chạy.
3. Lưu như trên.

### <a id="modes-and-borders"></a>Chế độ và đường viền

- **Đường viền màu xanh lá cây** xung quanh xe+odo → chụp/chỉnh sửa đồng hồ đo đường.
- **Đường viền màu xanh lá cây** xung quanh chi phí+khối lượng → chế độ bơm.
- **Save** vẫn bị tắt cho đến khi một phương tiện được chọn và ít nhất một trong số odo/chi phí/khối lượng có dữ liệu và OCR vẫn không chạy.

Mẹo trên màn hình (bên dưới dòng hướng dẫn): *Màn trập = chụp · Đĩa = lưu · ↕ = chế độ odo/bơm · ↔ = chi phí hoán đổi/khối lượng.*

---

## <a id="expenses"></a>Chi phí

### <a id="new-expense"></a>Chi phí mới

Menu → **Chi phí mới**.

![Biểu mẫu chi phí mới và điều khiển camera (ví dụ hình ảnh gạch ngang trong dải camera khi trống)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Save** (đĩa), **shutter** (ảnh biên nhận) hoặc **thư viện** (chọn ảnh).
2. Điền vào **Ngày**, **Xe**, **Nhà cung cấp**, **Mô tả**, **Số tiền** (ký hiệu tiền tệ có thể chạm vào), **Danh mục**, tùy chọn **Đồng hồ đo đường**.
3. Biên nhận nhiều trang: chụp thêm các trang nếu UI cung cấp tính năng phân trang (trang 0 là biên nhận chính).
4. **Lưu** vào kho lưu trữ (cục bộ trước; sao lưu ảnh và đồng bộ hóa bảng tính diễn ra ở chế độ nền khi được định cấu hình).

### <a id="expense-list"></a>Danh sách chi phí

Menu → **Báo cáo** → **Danh sách chi phí** — duyệt qua các chi phí phi nhiên liệu; mở một mục để chỉnh sửa.

![Danh sách chi phí](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Chỉnh sửa chi phí

Mở một hàng từ danh sách. Nhà cung cấp, số lượng, chủng loại, xe và mô tả chính xác. Nếu biên nhận chỉ có trong bản sao lưu ảnh (không có tệp cục bộ có thể đọc được), hãy sử dụng **Tìm nạp hình ảnh từ kho lưu trữ** khi được hiển thị (hoạt động trên các đích ảnh đã định cấu hình).

![Chỉnh sửa chi phí](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Bắt đầu chuyến đi

Menu → **Bắt đầu chuyến đi** (sau khi điền vào ngăn kéo). Chụp hoặc nhập đồng hồ đo đường, chọn loại chuyến đi, lưu bằng biểu tượng **đĩa**. **Dừng** là phím tắt dành cho Cá nhân hiện tại tại vị trí GPS được giữ. Sử dụng **ⓘ** để nhắc nhở kiểm soát.

![Bắt đầu chuyến đi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Số lần bắt đầu chuyến đi được lưu trữ dưới dạng hàng nhiên liệu với **Loại chuyến đi** (không phải lần nạp thông thường). Chúng xuất hiện trong **Báo cáo → Số dặm chuyến đi**, không phải trong Lịch sử nhiên liệu.

---

## <a id="reports"></a>Báo cáo

Menu → **Báo cáo** mở trung tâm sản phẩm (tóm tắt mọi thời đại + thẻ danh mục). Đây là bề mặt báo cáo sản phẩm duy nhất - không có mục ngăn kéo “Báo cáo & Biểu đồ” riêng biệt.

![Trung tâm báo cáo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Mở thẻ cho chế độ xe (**Tất cả / Mỗi / Đơn**), bộ lọc thời gian, biểu đồ và chia sẻ (**TEXT / CSV / PDF**). Thanh trên cùng về báo cáo trẻ em: **☰ + ←** (và **ⓘ** khi đăng ký).

### <a id="time-based-reports"></a>Báo cáo dựa trên thời gian

Thẻ biểu đồ chính. Các số liệu tùy chọn (mpg, khối lượng/khoảng cách chẳng hạn như G/mi, đơn giá chẳng hạn như $/G, chi phí/khoảng cách, $ hàng tháng, số dặm chuyến đi, % chuyến đi theo loại) với các thùng **Smooth** và **thang đo Y độc lập** (phổ thông bên trái; tiền và nhóm chuyến đi ở bên phải).

![Báo cáo dựa trên thời gian](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Báo cáo dựa trên thời gian - số liệu được cuộn](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Chi tiết toán kinh tế: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Lịch sử điền vs Lịch sử nhiên liệu

- **Báo cáo → Lịch sử điền** — điền theo trình tự thời gian cho các bộ lọc báo cáo (**chỉ điền**; không bắt đầu chuyến đi).

![Báo cáo lịch sử điền](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Lịch sử nhiên liệu** (nếu có trong điều hướng của công trình của bạn) — kho chứa đầy cho mỗi phương tiện, cũng chỉ lấp đầy; nhấn vào một hàng để chỉnh sửa.

### <a id="trip-miles"></a>Dặm chuyến đi

**Báo cáo → Số dặm chuyến đi** — số dặm theo loại, biểu đồ và thứ tự thời gian **danh sách điểm bắt đầu / phân đoạn chuyến đi**. Nhấn vào điểm bắt đầu thực sự để mở **Chỉnh sửa điền** cho hàng đó.

![Dặm chuyến đi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Chỉnh sửa điền

Từ Lịch sử điền, Lịch sử nhiên liệu hoặc Số dặm chuyến đi, hãy mở lần điền. Bố cục: xe và đồng hồ đo đường, **tiền tệ trước giá thành**, khối lượng, ghi chú. Loại chuyến đi chỉ xuất hiện khi hàng bắt đầu chuyến đi. Vị trí có phần tóm tắt cộng với **Chi tiết vị trí**. Thiếu ảnh cục bộ có nhận dạng đám mây: **Tìm nạp hình ảnh từ kho lưu trữ**.

![Chỉnh sửa điền — Tìm nạp từ kho lưu trữ khi thiếu ảnh cục bộ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Các thẻ danh mục khác bao gồm chi phí theo danh mục, tóm tắt phương tiện và danh sách chi phí.

Money sử dụng đơn vị tiền tệ của mỗi hàng khi được đặt. Tổng số tiền tệ hỗn hợp hiển thị **tổng phụ cho mỗi loại tiền tệ** (không có chuyển đổi ngoại hối im lặng).

---

## <a id="syncing"></a>Đang đồng bộ hóa

Menu → **Đồng bộ hóa** là trung tâm dành cho bảng tính và đích ảnh (không chỉ được chôn trong Cài đặt).

![Trung tâm đồng bộ hóa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Thẻ dành cho **Đồng bộ hóa bảng tính** và **Sao lưu ảnh** với trạng thái ngắn, **Đồng bộ hóa** cho loại đó và ******* vào danh sách đích.
- Mở đích cho **Kiểm tra kết nối** và **Đồng bộ hóa ngay (đích này)** / tất cả đã được định cấu hình.
- Lỗi **Chi tiết** và màu đỏ **!** trên thanh tiêu đề nằm ở đây.
- Thiết lập Google Trang tính và Drive từng bước: [Sao lưu và đồng bộ hóa nhiều thiết bị](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Cài đặt (tùy chọn cục bộ)

Menu → **Cài đặt**.

![Cài đặt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Cài đặt khác (cuộn)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Đối với các điểm đến, hãy chọn **Menu → Đồng bộ hóa**. Cài đặt vẫn có thể hiển thị các hàng tóm tắt mở cùng danh sách.

### <a id="local-preferences-common"></a>Tùy chọn cục bộ (phổ biến)

- **Lưu ảnh biên nhận nhiên liệu** / **Lưu ảnh chi phí cục bộ** — giữ hình ảnh trên thiết bị (có thể yêu cầu quyền Ảnh).
- **Phát âm thanh màn trập**
- **Tiền tệ** / **Đơn vị khối lượng** — mặc định của ứng dụng (hệ thống hoặc rõ ràng). Việc thay đổi đơn vị âm lượng với dữ liệu nhiên liệu hiện có có thể cung cấp hộp thoại chuyển đổi.
- **Chế độ tối**
- **Mẹo thiết lập** — mở lại hướng dẫn đồng bộ / xe chạy lần đầu.
- **Điền gỡ lỗi** / **Hiển thị màn hình thử nghiệm (dev)** — nâng cao; để lại để sử dụng hàng ngày. Màn hình thử nghiệm không được ghi lại ở đây.

CSV **xuất/nhập** (ZIP của thẻ Phương tiện / Chi phí / Nhiên liệu) có sẵn trong Cài đặt khi được bản dựng hiện tại cung cấp.

---

## <a id="help--about"></a>Trợ giúp & Giới thiệu

![Trợ giúp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Giới thiệu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Trợ giúp** — bắt đầu nhanh trên thiết bị, hướng dẫn thiết lập, liên kết tới hướng dẫn sử dụng này, chỉ mục thiết lập tự lưu trữ.
- **Giới thiệu** — phiên bản, giấy phép, GitHub, hướng dẫn sử dụng này (gói ngoại tuyến + HTML trực tuyến khi được xuất bản).

---

## <a id="related-docs"></a>Tài liệu liên quan

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — hướng dẫn ngắn gọn hàng ngày
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — thiết lập bảng/ảnh tự lưu trữ
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — hợp nhất, khôi phục, trùng lặp
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — chi tiết về số liệu kinh tế
