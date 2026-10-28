# TÀI LIỆU HƯỚNG DẪN BẢO MẬT TÍNH NĂNG IMPORT HỌC LIỆU
## TUÂN THỦ TIÊU CHUẨN OWASP TOP 10 (2021) VÀ PCI-DSS v4.0

---

## 1. CẢNH BÁO RỦI RO VÀ KIỂM SOÁT AN NINH TẦNG MẠNG (NETWORK ACCESS CONTROL)

### 1.1. Cảnh báo rủi ro an ninh
> [!WARNING]
> Ứng dụng Project Nihon được thiết kế là một ứng dụng tra cứu và học tập không sử dụng hệ thống tài khoản người dùng và không có phân quyền theo vai trò (Role-based Access Control). 
> **Rủi ro:** Bất kỳ ai có quyền truy cập vào endpoint `/import` đều có khả năng ghi dữ liệu vào cơ sở dữ liệu.

### 1.2. Biện pháp kiểm soát bù đắp (Compensating Controls)
Để bảo vệ an toàn cho hệ thống và cơ sở dữ liệu khi không có phân quyền người dùng, hệ thống triển khai cơ chế kiểm soát an ninh đa tầng:
1. **Kiểm soát kích hoạt tính năng (`app.import.enabled`):**
   - Giá trị mặc định: `true` (môi trường dev/local).
   - Khi cấu hình `app.import.enabled=false`, toàn bộ endpoint `/import/**` sẽ lập tức trả về mã lỗi **HTTP 404 Not Found**, ngăn chặn hoàn toàn việc dò quét hoặc gọi API.
2. **Kiểm soát dải mạng được phép (`app.import.allowed-ips`):**
   - Mặc định chỉ cho phép các kết nối từ máy nội bộ (`localhost`, `127.0.0.1`, `::1`) và các dải địa chỉ mạng riêng nội bộ (Private IP theo RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, cùng `fc00::/7`, `fe80::/10`).
   - Mọi kết nối đến từ địa chỉ IP nằm ngoài danh sách sẽ bị từ chối ngay lập tức với mã lỗi **HTTP 403 Forbidden** và thông báo lỗi chung.
3. **Bảo vệ chống giả mạo IP khi chạy sau Reverse Proxy (`app.import.trusted-proxies`):**
   - Tiêu đề `X-Forwarded-For` **chỉ được đọc khi kết nối trực tiếp đến từ các Reverse Proxy tin cậy** được cấu hình trước. Nếu kết nối trực tiếp từ client không đáng tin cậy, hệ thống tuyệt đối bỏ qua `X-Forwarded-For` và chỉ dùng `remoteAddr` của socket TCP.
4. **Giới hạn tần suất tải lên theo địa chỉ IP (`RateLimiterService`):**
   - Mỗi địa chỉ IP bị giới hạn tối đa 10 lần tải lên (preview/commit) trong vòng 1 phút (trả về HTTP 429 nếu vượt quá).
5. **Nhật ký kiểm toán (`import_audit_log`):**
   - Ghi lại địa chỉ IP của Client và định danh `"anonymous"` vào trường `user_id` cùng mã hash SHA-256, thời điểm và số lượng bản ghi.

### 1.3. Hướng dẫn vận hành khi triển khai công khai (Public Deployment Guide)
> [!IMPORTANT]
> **Khi đưa ứng dụng lên máy chủ Internet công khai:**
> - Nếu ứng dụng chỉ phục vụ người học tra cứu và không cần nhập thêm dữ liệu từ bên ngoài, **BẮT BUỘC** đặt:
>   ```properties
>   app.import.enabled=false
>   ```
>   hoặc thiết lập biến môi trường hệ thống:
>   ```bash
>   export APP_IMPORT_ENABLED=false
>   ```
> - Nếu quản trị viên cần cập nhật học liệu trên máy chủ production, hãy cấu hình `app.import.allowed-ips` trỏ đích danh tới IP tĩnh của máy chủ VPN quản trị nội bộ hoặc IP tĩnh của bộ phận biên tập:
>   ```properties
>   app.import.allowed-ips=192.168.1.100,10.8.0.5
>   ```

---

## 2. PHẠM VI HỆ THỐNG VÀ TUYÊN BỐ DỮ LIỆU THẺ THANH TOÁN (PCI-DSS)

### 2.1. Phạm vi tính năng (In-Scope)
Tính năng nhập dữ liệu học liệu từ tệp tin Excel (`/import/**`) được thiết kế phục vụ duy nhất mục đích giáo dục và quản lý nội dung tiếng Nhật:
- **Chủ đề bài học (Topic):** Tên chủ đề, số thứ tự sắp xếp, phân loại từ vựng hoặc Kanji.
- **Từ vựng (Vocabulary):** Từ tiếng Nhật, Romaji, Nghĩa tiếng Việt.
- **Chữ Hán tự (Kanji):** Ký tự Kanji (đúng 1 ký tự Han), Hán Việt, Ý nghĩa tiếng Việt, Âm On (Onyomi), Âm Kun (Kunyomi).
- **Từ ghép cần nhớ (KanjiWord):** Từ tiếng Nhật, Romaji, Nghĩa tiếng Việt liên kết với chữ Kanji.

### 2.2. Ngoài phạm vi dữ liệu thẻ thanh toán (Out-of-Scope for Cardholder Data)
- **Tuyên bố dứt khoát:** Tính năng nhập dữ liệu học liệu hoàn toàn **KHÔNG LƯU TRỮ, KHÔNG XỬ LÝ VÀ KHÔNG TRUYỀN TẢI** bất kỳ dữ liệu thẻ thanh toán nào, bao gồm nhưng không giới hạn ở:
  + Số tài khoản chính (PAN - Primary Account Number).
  + Mã xác thực / mã bảo mật thẻ (CVV, CVC, CID, CAV).
  + Ngày hết hạn thẻ (Expiration Date).
  + Họ tên chủ thẻ hoặc dữ liệu dải từ / chip (Track Data).
- **Cơ chế chặn chủ động (Active Defense):**
  + Khi phân tích cú pháp tệp Excel, hệ thống quét tự động toàn bộ nội dung của từng ô dữ liệu.
  + Nếu phát hiện bất kỳ chuỗi nào chứa từ 13 đến 19 chữ số liên tiếp (cho phép dấu cách hoặc dấu gạch ngang phân tách) và thỏa mãn thuật toán xác thực **Luhn (Mod 10)**, hàng dữ liệu đó sẽ bị từ chối ngay lập tức với thông báo lỗi:
    > `"Dữ liệu giống số thẻ thanh toán, không được nhập"`
  + Tuyệt đối **KHÔNG GHI GIÁ TRỊ SỐ THẺ** vào file log, audit log hay thông điệp trả về cho người dùng.
  + Giao dịch commit sẽ bị hoàn tác toàn bộ (Rollback toàn phần) nếu có bất kỳ dòng nào chứa dữ liệu nghi ngờ là số thẻ thanh toán.

---

## 3. BẢNG ĐỐI CHIẾU TIÊU CHUẨN PCI-DSS v4.0

| Yêu cầu PCI-DSS v4.0 | Nội dung kiểm soát | Trạng thái | Bằng chứng mã nguồn |
| :--- | :--- | :---: | :--- |
| **Yêu cầu 1: Cài đặt và duy trì kiểm soát an ninh mạng** | Giới hạn truy cập theo dải địa chỉ IP nội bộ (`allowed-ips`), vô hiệu hóa tính năng khi ra Internet (`app.import.enabled`). | **ĐẠT** | • [ImportSecurityInterceptor.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/ImportSecurityInterceptor.java)<br>• [ClientIpResolver.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/ClientIpResolver.java) |
| **Yêu cầu 3: Bảo vệ dữ liệu tài khoản lưu trữ** | Không lưu trữ dữ liệu chủ thẻ không cần thiết; chủ động chặn không để PAN lọt vào cơ sở dữ liệu. | **ĐẠT** | • [ExcelCellUtils.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelCellUtils.java#L162-L211)<br>• [WordSheetParser.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/WordSheetParser.java)<br>• [KanjiSheetParser.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/KanjiSheetParser.java) |
| **Yêu cầu 4: Bảo vệ dữ liệu bằng mật mã mạnh khi truyền tải** | Bắt buộc mã hóa dữ liệu truyền tải trên mạng công cộng qua giao thức TLS 1.2 trở lên với các bộ mã hóa mạnh. | **ĐẠT** | • [application-prod.properties](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/resources/application-prod.properties)<br>• [SecurityHeadersFilter.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/SecurityHeadersFilter.java)<br>• [TomcatHttpRedirectConfig.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/TomcatHttpRedirectConfig.java) |
| **Yêu cầu 6: Phát triển và duy trì phần mềm an toàn** | Quản lý lỗ hổng phụ thuộc, kiểm tra an toàn dữ liệu đầu vào, chống tấn công tiêm nhiễm (Injection), tách biệt môi trường dev và prod. | **ĐẠT** | • [ExcelUploadValidator.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelUploadValidator.java)<br>• [pom.xml](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/pom.xml)<br>• [application.properties](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/resources/application.properties) |
| **Yêu cầu 10: Ghi nhật ký và giám sát truy cập** | Ghi đầy đủ vết kiểm toán: ID, thời điểm, IP client, hành động, kết quả; không ghi dữ liệu nhạy cảm; chống Log Injection. | **ĐẠT** | • [ImportAuditLog.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/entity/ImportAuditLog.java)<br>• [ImportAuditService.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ImportAuditService.java)<br>• [ExcelCellUtils.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelCellUtils.java) |

---

## 4. BẢNG ĐỐI CHIẾU TIÊU CHUẨN OWASP TOP 10 (2021)

| Hạng mục OWASP | Biện pháp bảo vệ kỹ thuật | Trạng thái |
| :--- | :--- | :---: |
| **A01 Broken Access Control** | Kiểm soát truy cập bằng cách cô lập mạng qua `ImportSecurityInterceptor` (`app.import.allowed-ips` và `app.import.enabled`). Chống IDOR bằng cách gắn mã preview ngẫu nhiên (UUID) với địa chỉ IP client. | **ĐẠT** |
| **A02 Cryptographic Failures** | Bắt buộc TLS 1.2 & TLS 1.3, ciphers AEAD/ECDHE, cookie CSRF `SameSite=Lax`. Mã băm SHA-256 cho tệp. Không lưu mật khẩu/khóa dạng rõ. | **ĐẠT** |
| **A03 Injection** | 100% truy vấn cơ sở dữ liệu dùng Spring Data JPA Repository tham số hóa (Parameterized Queries). Giao diện Thymeleaf tự động escape HTML (`th:text`). Chống Formula Injection bằng hàm `sanitizeForExport`. | **ĐẠT** |
| **A04 Insecure Design** | Giới hạn tệp tối đa 5 MB, tối đa 5000 hàng và 20 cột/sheet. Giới hạn tần suất tải lên 10 lần/IP/phút (`RateLimiterService`). Toàn bộ commit gói gọn trong một `@Transactional` duy nhất. | **ĐẠT** |
| **A05 Security Misconfiguration** | Đầy đủ HTTP Security Headers (CSP, nosniff, DENY, HSTS, Permissions-Policy). Tắt directory listings, ẩn header phiên bản Server. Không lộ stack trace ra giao diện lỗi. Chống XXE trong Apache POI. | **ĐẠT** |
| **A06 Vulnerable & Outdated Components** | Audit phụ thuộc định kỳ qua build tool Maven. Sử dụng Java 21 LTS, Spring Boot 3.4.3, Apache POI 5.4.0. Không có thư viện chứa lỗ hổng mức High hoặc Critical. | **ĐẠT** |
| **A07 Identification & Authentication Failures** | Cơ chế CSRF Double Submit Cookie kết hợp form token cho các thao tác thay đổi dữ liệu (POST). Tệp tin xem trước tạm thời trong bộ nhớ tự động hủy sau 10 phút. | **ĐẠT** |
| **A08 Software & Data Integrity Failures** | Kiểm tra phần mở rộng chỉ nhận `.xlsx`. Kiểm tra Magic Bytes nhị phân `50 4B 03 04`. Từ chối macro (`vbaProject.bin`), liên kết ngoài và OLE Object. Không dùng Java Native Serialization. | **ĐẠT** |
| **A09 Security Logging & Monitoring Failures** | Nhật ký kiểm toán `import_audit_log` lưu vết IP client, SHA-256, số bản ghi. Ghi nhận `SUCCESS`, `FAILED`, `REJECTED`. Khử triệt để ký tự `\r`, `\n` chống Log Injection (CWE-117). | **ĐẠT** |
| **A10 Server-Side Request Forgery (SSRF)** | Hệ thống chỉ đọc dữ liệu trực tiếp từ luồng byte upload trong bộ nhớ, hoàn toàn không gọi hoặc fetch dữ liệu từ bất kỳ URL ngoại vi nào. Quét và từ chối file chứa liên kết ngoài. | **ĐẠT** |

---

## 5. QUẢN LÝ TÀI KHOẢN CƠ SỞ DỮ LIỆU ĐẶC QUYỀN TỐI THIỂU (LEAST PRIVILEGE)

Tài khoản dành riêng cho ứng dụng (`nihon_app`) chỉ được cấp các quyền hạn thiết yếu (`SELECT`, `INSERT`, `UPDATE`) trên các bảng dữ liệu thuộc phạm vi tính năng.

```sql
-- Cấp quyền thực thi tối thiểu trên cơ sở dữ liệu project_nihon
GRANT SELECT, INSERT, UPDATE ON project_nihon.topic TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.vocabulary TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.kanji_entry TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.kanji_word TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.import_audit_log TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.flyway_schema_history TO 'nihon_app'@'%';

-- Thu hồi các quyền nguy hiểm
REVOKE DELETE, DROP, ALTER, CREATE, GRANT OPTION ON project_nihon.* FROM 'nihon_app'@'%';
FLUSH PRIVILEGES;
```

---

## 6. QUY TẮC CẬP NHẬT DỮ LIỆU VÀ XỬ LÝ GIÁ TRỊ NULL (UPDATE_EXISTING)

### 6.1. Nguyên tắc bảo toàn dữ liệu hiện có
Khi người dùng thực hiện nhập dữ liệu với chế độ **`UPDATE_EXISTING`** (Cập nhật bản ghi trùng):
- **Quy tắc cốt lõi:** Ô trống hoặc chỉ chứa khoảng trắng trong file Excel khi import sẽ **KHÔNG GHI ĐÈ** lên dữ liệu đã tồn tại trong cơ sở dữ liệu.
- **Cơ chế:** Hệ thống chỉ cập nhật trường dữ liệu tương ứng khi ô trong file Excel thực sự có giá trị hợp lệ mới. Nếu ô trong file để trống, giá trị hiện tại trong database được bảo toàn nguyên vẹn.
- **Thêm mới (Insert):** Đối với các bản ghi mới hoàn toàn, ô trống hoặc chỉ chứa khoảng trắng sẽ được lưu thành `NULL` trong database (tuyệt đối không lưu chuỗi rỗng `""`).

### 6.2. Các cột cho phép giá trị NULL
Theo Migration V2:
- Bảng `kanji_entry`: các cột `han_viet`, `meaning_vi`, `onyomi`, `kunyomi` cho phép `NULL`. Ký tự `kanji_character` là cột bắt buộc duy nhất (đúng 1 ký tự Han).
- Bảng `vocabulary`: các cột `romaji`, `meaning_vi` cho phép `NULL`. Cột `japanese_text` là bắt buộc.
- Bảng `kanji_word`: các cột `romaji`, `meaning_vi` cho phép `NULL`. Cột `japanese` là bắt buộc.

