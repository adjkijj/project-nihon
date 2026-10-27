# Project Nihon - Hướng Dẫn Tính Năng Nhập Học Liệu Excel (Excel Import)

## 1. Giới thiệu tổng quan
Hệ thống **Project Nihon** hỗ trợ tính năng nhập học liệu hàng loạt từ tệp Excel (`.xlsx`), bao gồm:
- Danh mục **Chủ đề** (Topic).
- Danh mục **Từ vựng** (Vocabulary - Hiragana/Katakana/Kanji, Romaji, Nghĩa tiếng Việt).
- Danh mục **Chữ Hán Kanji** (Hán tự, Hán Việt, Ý nghĩa, Âm On, Âm Kun).
- Danh mục **Từ ghép cần nhớ của Kanji** (Kanji Word - Tiếng Nhật, Romaji, Nghĩa tiếng Việt).

Quy trình nhập dữ liệu tuân thủ chuẩn an toàn nghiêm ngặt theo **OWASP Top 10** và khuyến nghị **PCI-DSS v4.0**, bao gồm cơ chế **Xem trước (Preview)**, phát hiện và phòng chống trùng lặp, giao dịch nguyên tử (**Atomic Transaction**), và bộ lọc bảo mật nhiều lớp.

---

## 2. Cách chuẩn bị tệp Excel (.xlsx)

Để hệ thống nhận diện và nạp dữ liệu chính xác, tệp Excel phải đáp ứng các tiêu chuẩn sau:

### 2.1. Định dạng tệp tin & Quy chuẩn kỹ thuật
- **Định dạng cho phép:** Chỉ chấp nhận định dạng Microsoft Excel hiện đại `.xlsx` (OpenXML, Magic Bytes: `50 4B 03 04`).
- **Định dạng bị từ chối:** Tuyệt đối không chấp nhận `.xls` (cũ), `.xlsm`, `.xltm`, `.xlsb` (chứa Macro hoặc nhúng mã thực thi).
- **Kích thước tối đa:** Tối đa **5 MB** cho mỗi tệp tải lên.
- **Giới hạn số dòng/cột:** Tối đa **5.000 hàng** và **20 cột** trên mỗi sheet.
- **Quy tắc nội dung an toàn:**
  - Không chứa công thức tính toán (`=SUM...`, `=HYPERLINK...`, `+`, `-`, `@`) để chống tấn công **CSV/Formula Injection**.
  - Không chứa đối tượng OLE nhúng hoặc liên kết tài nguyên bên ngoài (External Links).
  - Không chứa dữ liệu nhạy cảm hoặc chuỗi 13-19 chữ số thỏa mãn thuật toán Luhn (dữ liệu giống số thẻ thanh toán PAN). Mọi chuỗi nghi vấn sẽ bị từ chối nhập để bảo đảm tuân thủ PCI-DSS.

---

### 2.2. Cấu trúc hai Sheet trong tệp Excel

Tệp Excel bắt buộc phải có ít nhất 2 sheet với tên (không phân biệt hoa thường):
1. `New Word` (Nhập từ vựng)
2. `Kanji` (Nhập chữ Hán và từ vựng liên quan)

*(Bạn có thể tải tệp mẫu chuẩn `Update.xlsx` ngay trên giao diện nhập liệu hoặc tại thư mục `src/main/resources/static/template/Update.xlsx`).*

#### A. Sheet `New Word` (Từ vựng)
- **Hàng 1 (Header):** Bắt buộc gồm 4 cột tiêu đề chính xác:
  | Cột A | Cột B | Cột C | Cột D |
  | :--- | :--- | :--- | :--- |
  | **Topic** | **Tiếng Nhật** | **Romaji** | **Nghĩa tiếng Việt** |
- **Dữ liệu (Bắt đầu từ Hàng 2):**
  - **Cột A (Topic):** Tên chủ đề. Khi Cột A có giá trị, chủ đề đó sẽ được áp dụng cho hàng hiện tại. Nếu Cột A để trống, hệ thống sẽ **tự động kế thừa chủ đề** của hàng phía trên liền kề.
  - **Cột B (Tiếng Nhật):** Bắt buộc nhập (từ viết bằng Kanji, Hiragana hoặc Katakana).
  - **Cột C (Romaji):** Cách đọc phiên âm Latin.
  - **Cột D (Nghĩa tiếng Việt):** Bắt buộc nhập nghĩa tiếng Việt có dấu.
  - Hàng trống hoàn toàn sẽ tự động được bỏ qua.

#### B. Sheet `Kanji` (Chữ Hán & Từ vựng ghép)
- **Hàng 1 & Hàng 2 (Header):** Tiêu đề 2 tầng với ô merge `G1:I1` có nhãn **"Word"**:
  | Hàng | Cột A | Cột B | Cột C | Cột D | Cột E | Cột F | Cột G | Cột H | Cột I |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | **Hàng 1** | Topic | Kanji | Hán Việt | Ý nghĩa | On | Kun | <td colspan="3" align="center">**Word** (G1:I1)</td> |
  | **Hàng 2** | *(trống)* | *(trống)* | *(trống)* | *(trống)* | *(trống)* | *(trống)* | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
- **Dữ liệu (Bắt đầu từ Hàng 3):**
  - **Hàng bắt đầu Kanji mới (Cột B có giá trị):**
    - **Cột A (Topic):** Chủ đề của chữ Hán (kế thừa nếu để trống).
    - **Cột B (Kanji):** Bắt buộc đúng **1 ký tự chữ Hán duy nhất**.
    - **Cột C (Hán Việt):** Âm Hán Việt (ví dụ: `BA`, `KIỀU`, `NHẬT`).
    - **Cột D (Ý nghĩa):** Ý nghĩa khái quát (tiếng Việt có dấu).
    - **Cột E (On):** Âm On (Katakana hoặc Romaji).
    - **Cột F (Kun):** Âm Kun (Hiragana hoặc Romaji).
    - **Cột G, H, I:** Có thể chứa từ ghép đầu tiên của chữ Kanji này (hoặc để trống nếu Kanji không có từ ghép ban đầu).
  - **Hàng từ ghép bổ sung (Cột B trống):**
    - Khi Cột B để trống nhưng Cột G, H, I có dữ liệu, hệ thống tự động gán từ vựng này vào chữ Hán của khối gần nhất phía trên.
    - Một chữ Hán có thể có 0, 1 hoặc nhiều từ ghép tương ứng.

---

## 3. Cách sử dụng giao diện người dùng (/import)

### 3.1. Phân quyền truy cập
- Tính năng nhập học liệu được bảo vệ nghiêm ngặt: chỉ tài khoản có vai trò **ADMIN** (hoặc **TEACHER** nếu có cấu hình) mới có quyền truy cập URL `/import` và các API xử lý.
- Người dùng chưa đăng nhập hoặc không đủ thẩm quyền sẽ bị từ chối với mã phản hồi HTTP `403 Forbidden` tại tầng máy chủ.

### 3.2. Quy trình 3 bước thực hiện
1. **Truy cập:** Đăng nhập hệ thống và chuyển đến mục **"Nhập Excel"** (`/import`) trên thanh điều hướng.
2. **Tải file mẫu (Nếu cần):** Nhấn nút **"Tải file mẫu Update.xlsx"** để nhận tệp cấu trúc chuẩn đã được định dạng sẵn.
3. **Bước 1 - Xem trước (Preview):**
   - Nhấn "Chọn tệp tin Excel (.xlsx)" và tải lên tệp của bạn.
   - Nhấn **"Xem Trước Dữ Liệu"**.
   - Hệ thống tiến hành xác thực an toàn nhiều lớp (chống zip bomb, kiểm tra magic bytes, chặn macro, chặn formula, kiểm tra thẻ thanh toán) và tổng hợp số lượng:
     * Số chủ đề mới.
     * Số từ vựng mới / từ vựng trùng lặp.
     * Số Kanji mới / Kanji trùng lặp.
     * Số từ Kanji mới.
     * Bảng danh sách cảnh báo lỗi hàng (nếu có vi phạm).
   - *Lưu ý:* Quá trình Preview **không ghi bất kỳ dữ liệu nào** vào cơ sở dữ liệu.
4. **Bước 2 - Lựa chọn chế độ nhập (Import Mode):**
   - **Bỏ qua bản ghi trùng (SKIP_DUPLICATES - Mặc định):** Giữ nguyên các từ vựng và Kanji đã có trong hệ thống, chỉ thêm các mục chưa từng xuất hiện.
   - **Cập nhật bản ghi trùng (UPDATE_EXISTING):** Ghi đè âm đọc, Romaji, âm Hán Việt và nghĩa tiếng Việt mới vào các bản ghi đã tồn tại.
5. **Bước 3 - Xác nhận nhập dữ liệu (Commit):**
   - Nhấn **"Xác Nhận Nhập Dữ Liệu"**.
   - Yêu cầu commit sử dụng mã phiên tạm thời có thời hạn 10 phút (`previewToken`) kèm mã chống giả mạo `_csrf`.
   - Toàn bộ quá trình commit diễn ra trong một **Transaction nguyên tử**. Nếu xảy ra lỗi dữ liệu, toàn bộ giao dịch sẽ tự động rollback hoàn toàn, đảm bảo tính toàn vẹn 100% cho cơ sở dữ liệu.

---

## 4. Cấu hình hệ thống cần thiết

Toàn bộ thông số cấu hình được quản lý tập trung trong file `application.properties` (hoặc biến môi trường), dễ dàng tùy biến khi triển khai:

### 4.1. Cấu hình tải tệp & Giới hạn Multipart
```properties
# Giới hạn kích thước tệp tải lên (Mặc định 5 MB)
nihon.upload.excel.max-file-size-bytes=5242880
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB

# Giới hạn số hàng và cột tối đa mỗi sheet
nihon.upload.excel.max-rows-per-sheet=5000
nihon.upload.excel.max-columns-per-sheet=20

# Bảo vệ chống Zip Bomb (Apache POI ZipSecureFile)
nihon.upload.excel.min-inflate-ratio=0.01
nihon.upload.excel.max-entry-size=10485760
nihon.upload.excel.max-text-size=10485760
```

### 4.2. Giới hạn tần suất (Rate Limiting)
Hệ thống tích hợp bộ lọc tần suất `RateLimitFilter`:
- Mỗi người dùng tối đa **10 lần tải lên / phút**.
- Nếu vượt quá ngưỡng, hệ thống lập tức phản hồi mã `HTTP 429 Too Many Requests` và ghi vết cảnh báo.

### 4.3. Cấu hình HTTPS & Production (`application-prod.properties`)
- Bật profile sản xuất: `--spring.profiles.active=prod`
- Bắt buộc giao thức an toàn: Chỉ hỗ trợ **TLS 1.2** và **TLS 1.3**; loại bỏ hoàn toàn các giao thức và cipher yếu.
- Thiết lập đường dẫn Keystore và mật khẩu thông qua biến môi trường bảo mật:
  ```bash
  export SSL_KEY_STORE_PATH=/etc/ssl/certs/nihon-keystore.p12
  export SSL_KEY_STORE_PASSWORD=StrongEnvSecretPasswordHere
  export SSL_KEY_STORE_TYPE=PKCS12
  export HTTPS_REDIRECT_ENABLED=true
  ```
- **Bảo mật phản hồi HTTP Headers:** Tự động đính kèm `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- **Ẩn thông tin nhạy cảm:** Tắt hoàn toàn stack trace và whitelabel error page (`server.error.include-stacktrace=never`).

---

## 5. Hướng dẫn chạy kiểm thử (Testing)

Để chạy toàn bộ test suite và xác nhận các ca kiểm thử an toàn:
```bash
# Chạy toàn bộ kiểm thử
./mvnw clean test

# Chạy riêng bộ kiểm thử tích hợp toàn trình (E2E Import Flow & Negative Security Cases)
./mvnw test -Dtest=EndToEndImportFlowTest

# Chạy kiểm thử bộ lọc thẻ thanh toán Luhn (PCI-DSS)
./mvnw test -Dtest=PaymentCardFilterTest
```
