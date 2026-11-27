# BÁO CÁO RÀ SOÁT BẢO MẬT LỊCH SỬ GIT (GIT HISTORY SECURITY AUDIT)
**Mã phát hiện liên quan:** SEC-008 (`docs/SECURITY_AUDIT_REPORT.md`)  
**Ngày thực hiện:** 16/11/2026  
**Chính sách xử lý:** Không viết lại lịch sử Git (Do Not Rewrite Git History) — Bắt buộc xoay vòng mật khẩu thật (Mandatory Password Rotation).

---

## 1. Lý do không viết lại lịch sử Git

Theo yêu cầu và nguyên tắc bảo toàn tính toàn vẹn của kho mã nguồn phân tán:
1. **Không sử dụng công cụ viết lại lịch sử (`git-filter-repo`, `git filter-branch`, `git rebase -i`):**
   - Việc viết lại lịch sử sẽ làm thay đổi toàn bộ mã băm SHA-1 của tất cả các commit từ điểm khởi đầu.
   - Gây xung đột nghiêm trọng (diverged history) cho tất cả các nhà phát triển và các nhánh làm việc độc lập đang clone repo.
   - Làm mất dấu vết lịch sử đối soát và kiểm toán (audit trail) của các bản vá lỗi trước đây.
2. **Chiến lược an ninh bù đắp:**
   - Thay vì xóa dấu vết trong lịch sử cũ, chiến lược bảo mật chuẩn quy định: **Xem toàn bộ thông tin/mật khẩu từng xuất hiện trong commit là đã bị lộ và tiến hành đổi mới hoàn toàn tại nơi đang sử dụng**.

---

## 2. Danh mục chính xác thông tin cá nhân và email trong lịch sử Git

Qua rà soát toàn bộ lịch sử Git của repository:

| STT | Địa chỉ Email | Tên Author / Committer | Số lượng Commit xuất hiện | Ghi chú & Đánh giá rủi ro |
| :---: | :--- | :--- | :---: | :--- |
| 1 | `nguyenvietkhoa405@gmail.com` | `adkijj` | Toàn bộ 60+ commits (từ commit `dc6b076` đến nay) | Email cá nhân của nhà phát triển phụ trách ban đầu. Rủi ro: Thu thập thông tin cho tấn công Phishing / Social Engineering. |

* **Khuyến nghị cho tương lai:** Thiết lập danh tính Git ẩn danh hoặc email dạng `noreply` của tổ chức (ví dụ: `developer@users.noreply.github.com`) cho các commit mới thông qua lệnh:
  ```bash
  git config user.name "Project Nihon Team"
  git config user.email "dev@nihon.local"
  ```

---

## 3. Danh mục chính xác mật khẩu và dữ liệu cấu hình trong lịch sử Git

| STT | Mật khẩu / Khóa bí mật | Commit liên quan | Tệp tin xuất hiện trong lịch sử | Phân loại & Mục đích sử dụng |
| :---: | :--- | :---: | :--- | :--- |
| 1 | `123456` | `46a71bc`<br>`da5b92c`<br>`6ddc0d5` | `application-dev.properties`<br>Mã kiểm tra kết nối Python | Mật khẩu tài khoản `root` kết nối MySQL cục bộ trong môi trường dev. |
| 2 | `devpassword123` | `250ee2b` | `docs/SECURITY_IMPORT.md` | Mật khẩu tệp Keystore SSL (`-storepass`, `-keypass`) trong tài liệu hướng dẫn cũ. |
| 3 | `MatKhauPhucTap_ThayBangGiaTriThat_2026!` | `250ee2b` | `docs/SECURITY_IMPORT.md` (Dòng 125) | Mật khẩu mẫu cấu hình biến môi trường `DB_PASSWORD` trong tài liệu kiểm toán. |
| 4 | `StrongEnvSecretPasswordHere` | `250ee2b` | `docs/SECURITY_IMPORT.md` | Mật khẩu mẫu biến môi trường `SSL_KEY_STORE_PASSWORD`. |
| 5 | `MyStr0ng!AdminPass#2026` | `ddc0f93` | `README.md`<br>`.env.example` | Mật khẩu quản trị mẫu cho `APP_ADMIN_PASSWORD`. |

---

## 4. Đường dẫn cục bộ (Windows Local Paths) trong lịch sử Git

Trong các commit `250ee2b` và `4ed3344` (tệp `docs/SECURITY_IMPORT.md` trước khi được dọn dẹp ở commit `dd53ef6`):
- Đường dẫn: `file:///C:/Users/nguye/OneDrive/Documents/FALL26/OJT/Workspace/PROJECT%20NIHON/...`
- Thông tin bị lộ: Tên người dùng Windows (`nguye`) và cấu trúc thư mục học tập nội bộ.

---

## 5. Quy định bắt buộc: Đổi mật khẩu thật ở nơi sử dụng (Password Rotation)

> [!CAUTION]
> Bất kỳ mật khẩu nào đã từng xuất hiện dưới dạng văn bản rõ trong lịch sử Git đều **PHẢI ĐƯỢC COI LÀ KHÔNG AN TOÀN** và bắt buộc phải thay đổi ngay lập tức trên các môi trường triển khai thực tế.

### 5.1. Cơ sở dữ liệu MySQL (Môi trường Dev và Production)
1. Đăng nhập vào hệ quản trị MySQL và đổi mật khẩu cho tài khoản `root` và tài khoản ứng dụng `nihon_app`:
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY '<MẬT_KHẨU_MỚI_NGẪU_NHIÊN_TỐI_THIỂU_16_KÝ_TỰ>';
   ALTER USER 'nihon_app'@'%' IDENTIFIED BY '<MẬT_KHẨU_MỚI_NGẪU_NHIÊN_TỐI_THIỂU_16_KÝ_TỰ>';
   FLUSH PRIVILEGES;
   ```
2. Cập nhật biến môi trường `DB_PASSWORD` trên máy chủ vận hành, **tuyệt đối không ghi đè vào file `application.properties`**.

### 5.2. Phân hệ Quản trị (`APP_ADMIN_PASSWORD`)
1. Sinh chuỗi mật khẩu quản trị ngẫu nhiên mới:
   ```bash
   openssl rand -base64 24
   ```
2. Thiết lập biến môi trường hệ thống cho ứng dụng:
   - **Linux / Docker:**
     ```bash
     export APP_ADMIN_PASSWORD="<CHUỖI_MẬT_KHẨU_MỚI_VỪA_SINH>"
     ```
   - **Windows PowerShell:**
     ```powershell
     $env:APP_ADMIN_PASSWORD="<CHUỖI_MẬT_KHẨU_MỚI_VỪA_SINH>"
     ```
3. Tuyệt đối không dùng lại giá trị mẫu `MyStr0ng!AdminPass#2026`.

### 5.3. Chứng chỉ TLS/HTTPS Keystore (`SSL_KEY_STORE_PASSWORD`)
1. Nếu sử dụng Spring Boot phục vụ HTTPS trực tiếp, hãy tạo tệp Keystore mới với mật khẩu mới hoàn toàn:
   ```bash
   keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 \
     -keystore nihon-server.p12 -storepass <MẬT_KHẨU_KEYSTORE_MỚI>
   ```
2. Thiết lập biến môi trường `SSL_KEY_STORE_PASSWORD` trên hệ điều hành của máy chủ triển khai.

