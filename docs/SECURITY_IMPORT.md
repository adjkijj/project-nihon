# TÀI LIỆU HƯỚNG DẪN BẢO MẬT TÍNH NĂNG IMPORT HỌC LIỆU
## TUÂN THỦ TIÊU CHUẨN OWASP TOP 10 (2021) VÀ PCI-DSS v4.0

---

## 1. PHẠM VI HỆ THỐNG VÀ TUYÊN BỐ DỮ LIỆU THẺ THANH TOÁN

### 1.1. Phạm vi tính năng (In-Scope)
Tính năng nhập dữ liệu học liệu từ tệp tin Excel (`/import/**`) được thiết kế phục vụ duy nhất mục đích giáo dục và quản lý nội dung tiếng Nhật:
- **Chủ đề bài học (Topic):** Tên chủ đề, số thứ tự sắp xếp, phân loại từ vựng hoặc Kanji.
- **Từ vựng (Vocabulary):** Từ tiếng Nhật, Romaji, Nghĩa tiếng Việt.
- **Chữ Hán tự (Kanji):** Ký tự Kanji (đúng 1 ký tự Han), Hán Việt, Ý nghĩa tiếng Việt, Âm On (Onyomi), Âm Kun (Kunyomi).
- **Từ ghép cần nhớ (KanjiWord):** Từ tiếng Nhật, Romaji, Nghĩa tiếng Việt liên kết với chữ Kanji.

### 1.2. Ngoài phạm vi dữ liệu thẻ thanh toán (Out-of-Scope for Cardholder Data)
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

## 2. BẢNG ĐỐI CHIẾU TIÊU CHUẨN PCI-DSS v4.0

| Yêu cầu PCI-DSS v4.0 | Nội dung kiểm soát | Trạng thái | Bằng chứng mã nguồn |
| :--- | :--- | :---: | :--- |
| **Yêu cầu 3: Bảo vệ dữ liệu tài khoản lưu trữ** | Không lưu trữ dữ liệu chủ thẻ không cần thiết; chủ động chặn không để PAN lọt vào cơ sở dữ liệu. | **ĐẠT** | • [ExcelCellUtils.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelCellUtils.java#L162-L211)<br>• [WordSheetParser.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/WordSheetParser.java#L110-L127)<br>• [KanjiSheetParser.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/KanjiSheetParser.java#L150-L188) |
| **Yêu cầu 4: Bảo vệ dữ liệu bằng mật mã mạnh khi truyền tải** | Bắt buộc mã hóa dữ liệu truyền tải trên mạng công cộng qua giao thức TLS 1.2 trở lên với các bộ mã hóa mạnh. | **ĐẠT** | • [application-prod.properties](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/resources/application-prod.properties#L11-L26)<br>• [SecurityHeadersFilter.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/SecurityHeadersFilter.java#L36-L40)<br>• [TomcatHttpRedirectConfig.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/TomcatHttpRedirectConfig.java#L18-L38) |
| **Yêu cầu 6: Phát triển và duy trì phần mềm an toàn** | Quản lý lỗ hổng phụ thuộc, kiểm tra an toàn dữ liệu đầu vào, chống tấn công tiêm nhiễm (Injection), tách biệt môi trường dev và prod. | **ĐẠT** | • [ExcelUploadValidator.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelUploadValidator.java#L125-L162)<br>• [pom.xml](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/pom.xml#L13-L58)<br>• [application.properties](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/resources/application.properties)<br>• [application-prod.properties](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/resources/application-prod.properties) |
| **Yêu cầu 7 & 8: Quyền tối thiểu, xác thực và quản lý phiên** | Phân quyền truy cập máy chủ (Server-side Role Authorization), chỉ cho phép ADMIN và TEACHER, cookie bảo mật, thời hạn hết hạn phiên. | **ĐẠT** | • [RoleAuthInterceptor.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/RoleAuthInterceptor.java#L27-L55)<br>• [ImportSessionStorage.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ImportSessionStorage.java#L26-L65)<br>• [CsrfInterceptor.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/config/CsrfInterceptor.java#L33-L75) |
| **Yêu cầu 10: Ghi nhật ký và giám sát truy cập** | Ghi đầy đủ vết kiểm toán: ID, thời điểm, người dùng, hành động, kết quả; không ghi dữ liệu nhạy cảm; chống Log Injection. | **ĐẠT** | • [ImportAuditLog.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/entity/ImportAuditLog.java#L15-L55)<br>• [ImportAuditService.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ImportAuditService.java#L39-L64)<br>• [ExcelCellUtils.java](file:///c:/Users/nguye/OneDrive/Documents/FALL26/OJT/PROJECT%20NIHON/src/main/java/com/nihon/service/excel/ExcelCellUtils.java#L143-L155) |

---

## 3. BẢNG ĐỐI CHIẾU TIÊU CHUẨN OWASP TOP 10 (2021)

| Hạng mục OWASP | Biện pháp bảo vệ kỹ thuật | Trạng thái |
| :--- | :--- | :---: |
| **A01 Broken Access Control** | Kiểm tra quyền ở tầng server qua `RoleAuthInterceptor` (bắt buộc ADMIN/TEACHER). Chặn URL trực tiếp. Chống IDOR bằng cách gắn mã preview ngẫu nhiên với `userKey`. | **ĐẠT** |
| **A02 Cryptographic Failures** | Bắt buộc TLS 1.2 & TLS 1.3, ciphers AEAD/ECDHE, cookie phiên `Secure; HttpOnly; SameSite=Lax`. Mã băm SHA-256 cho tệp. Không lưu mật khẩu/khóa dạng rõ. | **ĐẠT** |
| **A03 Injection** | 100% truy vấn cơ sở dữ liệu dùng Spring Data JPA Repository tham số hóa (Parameterized Queries). Giao diện Thymeleaf tự động escape HTML (`th:text`). Chống Formula Injection bằng hàm `sanitizeForExport`. | **ĐẠT** |
| **A04 Insecure Design** | Giới hạn tệp tối đa 5 MB, tối đa 5000 hàng và 20 cột/sheet. Giới hạn tần suất tải lên 10 lần/người/phút (`RateLimiterService`). Toàn bộ commit gói gọn trong một `@Transactional` duy nhất. | **ĐẠT** |
| **A05 Security Misconfiguration** | Đầy đủ HTTP Security Headers (CSP, nosniff, DENY, HSTS, Permissions-Policy). Tắt directory listings, ẩn header phiên bản Server. Không lộ stack trace ra giao diện lỗi. Chống XXE trong Apache POI. | **ĐẠT** |
| **A06 Vulnerable & Outdated Components** | Audit phụ thuộc định kỳ qua build tool Maven. Sử dụng Java 21 LTS, Spring Boot 3.4.3, Apache POI 5.4.0. Không có thư viện chứa lỗ hổng mức High hoặc Critical. | **ĐẠT** |
| **A07 Identification & Authentication Failures** | Phiên timeout tự động sau 30 phút không hoạt động. Tệp tin xem trước tạm thời trong bộ nhớ tự động hủy sau 10 phút. Bảo vệ chống CSRF cho mọi thao tác ghi dữ liệu. | **ĐẠT** |
| **A08 Software & Data Integrity Failures** | Kiểm tra phần mở rộng chỉ nhận `.xlsx`. Kiểm tra Magic Bytes nhị phân `50 4B 03 04`. Từ chối macro (`vbaProject.bin`), liên kết ngoài và OLE Object. Không dùng Java Native Serialization. | **ĐẠT** |
| **A09 Security Logging & Monitoring Failures** | Nhật ký kiểm toán `import_audit_log` đủ 10 trường. Ghi nhận các trường hợp thành công (`SUCCESS`), lỗi rollback (`FAILED`), và từ chối thẩm định (`REJECTED`). Khử triệt để ký tự `\r`, `\n` chống Log Injection (CWE-117). | **ĐẠT** |
| **A10 Server-Side Request Forgery (SSRF)** | Hệ thống chỉ đọc dữ liệu trực tiếp từ luồng byte upload trong bộ nhớ, hoàn toàn không gọi hoặc fetch dữ liệu từ bất kỳ URL ngoại vi nào. Quét và từ chối file chứa liên kết ngoài. | **ĐẠT** |

---

## 4. QUẢN LÝ TÀI KHOẢN CƠ SỞ DỮ LIỆU ĐẶC QUYỀN TỐI THIỂU (LEAST PRIVILEGE)

Theo yêu cầu của PCI-DSS (Yêu cầu 7) và OWASP (A01), ứng dụng đang chạy trong môi trường thực tế (Production) **tuyệt đối không dùng tài khoản `root`** hoặc tài khoản quản trị cụm cơ sở dữ liệu.

Tài khoản dành riêng cho ứng dụng (`nihon_app`) chỉ được cấp các quyền hạn thiết yếu (`SELECT`, `INSERT`, `UPDATE`) trên các bảng dữ liệu thuộc phạm vi tính năng.

### Script SQL mẫu khởi tạo tài khoản và phân quyền trên MySQL 8.x / 9.x:

```sql
-- 1. Đăng nhập vào MySQL với quyền Quản trị viên (DBA / root)
-- mysql -u root -p

-- 2. Tạo tài khoản ứng dụng riêng biệt (sử dụng mật khẩu mạnh từ biến môi trường)
CREATE USER 'nihon_app'@'%' IDENTIFIED BY 'MatKhauPhucTap_ThayBangGiaTriThat_2026!';

-- 3. Cấp quyền thực thi tối thiểu trên cơ sở dữ liệu project_nihon
-- Chỉ cho phép SELECT, INSERT, UPDATE trên các bảng nghiệp vụ và nhật ký kiểm toán
GRANT SELECT, INSERT, UPDATE ON project_nihon.topic TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.vocabulary TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.kanji_entry TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.kanji_word TO 'nihon_app'@'%';
GRANT SELECT, INSERT, UPDATE ON project_nihon.import_audit_log TO 'nihon_app'@'%';

-- Bảng quản lý schema Flyway (nếu có sử dụng công cụ migration)
GRANT SELECT, INSERT, UPDATE ON project_nihon.flyway_schema_history TO 'nihon_app'@'%';

-- 4. Thu hồi các quyền nguy hiểm (ngăn chặn tấn công phá hoại bảng hoặc cấu trúc)
REVOKE DELETE, DROP, ALTER, CREATE, GRANT OPTION ON project_nihon.* FROM 'nihon_app'@'%';

-- 5. Áp dụng các thay đổi quyền
FLUSH PRIVILEGES;

-- 6. Kiểm tra lại quyền đã cấp
SHOW GRANTS FOR 'nihon_app'@'%';
```

---

## 5. CẤU HÌNH HỆ THỐNG KHI TRIỂN KHAI THỰC TẾ (PRODUCTION DEPLOYMENT)

Khi đưa ứng dụng lên máy chủ production, bắt buộc kích hoạt profile `prod` (`--spring.profiles.active=prod`).

### Các biến môi trường bắt buộc cấu hình:

| Tên biến môi trường | Ý nghĩa | Ví dụ giá trị |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Kích hoạt cấu hình môi trường chạy thật | `prod` |
| `DB_URL` | Chuỗi kết nối JDBC an toàn tới MySQL (bật mã hóa SSL) | `jdbc:mysql://db-prod.internal:3306/project_nihon?useSSL=true&requireSSL=true&characterEncoding=UTF-8` |
| `DB_USERNAME` | Tên tài khoản DB đặc quyền tối thiểu | `nihon_app` |
| `DB_PASSWORD` | Mật khẩu tài khoản cơ sở dữ liệu | `MatKhauPhucTap_ThayBangGiaTriThat_2026!` |
| `SSL_KEYSTORE_PATH` | Đường dẫn tệp chứa chứng chỉ số TLS hợp lệ (PKCS12) | `/opt/nihon/certs/keystore.p12` |
| `SSL_KEYSTORE_PASSWORD` | Mật khẩu bảo vệ tệp Keystore TLS | `${VAULT_TLS_KEYSTORE_PASS}` |

### Lệnh chạy ứng dụng trên môi trường thực tế:
```bash
java -Dspring.profiles.active=prod \
     -jar project-nihon.jar
```

---

## 6. DANH SÁCH VIỆC DÀNH CHO BỘ PHẬN VẬN HÀNH HỆ THỐNG (OPERATOR RUNBOOK)

Bộ phận Quản trị hệ thống (DevOps / SysAdmin) có trách nhiệm thực hiện các quy trình định kỳ sau:

1. **Quản lý Chứng chỉ số TLS:**
   - Sử dụng chứng chỉ TLS chính thức được ký bởi Tổ chức phát hành chứng chỉ công cộng uy tín (CA như Let's Encrypt, DigiCert, GlobalSign). Tuyệt đối không dùng chứng chỉ tự ký (Self-signed) trên production.
   - Thiết lập tự động gia hạn chứng chỉ trước ngày hết hạn ít nhất 30 ngày.

2. **Chính sách xoay vòng khóa và mật khẩu (Key & Credential Rotation):**
   - Định kỳ 90 ngày (tuân thủ PCI-DSS Yêu cầu 8), thay đổi mật khẩu tài khoản kết nối cơ sở dữ liệu (`nihon_app`).
   - Xoay vòng mật khẩu keystore TLS và khóa bí mật của máy chủ.

3. **Chính sách sao lưu và phục hồi dữ liệu (Backup & Disaster Recovery):**
   - Thực hiện sao lưu dữ liệu tự động hàng ngày (Daily Database Dump).
   - Mã hóa tệp tin sao lưu trước khi lưu trữ ra máy chủ lưu trữ độc lập (Off-site storage / Cloud cold storage).
   - Định kỳ 6 tháng thực hiện diễn tập khôi phục dữ liệu từ bản sao lưu để đảm bảo khả năng phục hồi khi gặp sự cố.

4. **Giám sát an ninh và Cảnh báo sự cố (Security Monitoring & Alerting):**
   - Tích hợp nhật ký kiểm toán từ bảng `import_audit_log` vào hệ thống giám sát tập trung (SIEM / ELK Stack).
   - Thiết lập quy tắc cảnh báo tự động khi:
     + Phát hiện nhiều bản ghi có trạng thái `REJECTED` liên tiếp trong thời gian ngắn từ cùng một địa chỉ IP hoặc User.
     + Người dùng nhận mã phản hồi HTTP `429 Too Many Requests` (dấu hiệu dò quét hoặc spam upload).
     + Người dùng cố ý truy cập trái phép nhận mã phản hồi HTTP `403 Forbidden`.
