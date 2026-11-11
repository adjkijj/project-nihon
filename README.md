# Project Nihon - Hệ Thống Quản Lý & Luyện Tập Từ Vựng, Kanji Tiếng Nhật

## 1. Giới thiệu tổng quan & Danh sách chức năng

**Project Nihon** là ứng dụng web quản lý học liệu và luyện tập tiếng Nhật (Từ vựng & Hán tự Kanji) được xây dựng trên nền tảng **Java 21**, **Spring Boot 3.4**, **Spring Data JPA**, **Thymeleaf** và **Apache POI**. Hệ thống tuân thủ chặt chẽ các nguyên tắc bảo mật **OWASP Top 10** và **PCI-DSS v4.0**.

### 1.1. Quản lý Chủ đề (`/topics`)
- **Cây chủ đề 2 cấp:** Chỉ cho phép chủ đề gốc (`parent == null`) làm cha của chủ đề con; chặn vòng lặp và chặn chuyển chủ đề đang có con thành chủ đề con.
- **Đồng bộ nhánh Kanji (`kanjiSection`):** Chủ đề con luôn kế thừa trạng thái `kanjiSection` của chủ đề gốc; khi đổi `kanjiSection` ở chủ đề gốc, toàn bộ chủ đề con được cập nhật đồng bộ trong cùng một giao dịch.
- **Thao tác quản lý:** Tạo mới, chỉnh sửa, di chuyển thứ tự lên/xuống (`UP`/`DOWN`) trong cùng cấp cha, xem trang chi tiết (`/topics/{id}`), chuyển toàn bộ nội dung (từ vựng, Kanji, chủ đề con) sang chủ đề khác (`/topics/{id}/move-content`) rồi xóa chủ đề rỗng.

### 1.2. Quản lý Từ vựng (`/vocabulary`)
- **Hiển thị gộp nhóm thông minh (`VocabularyRowDto`):** Các bản ghi từ vựng trùng `japaneseText` ở nhiều chủ đề được gộp thành một dòng hiển thị kèm danh sách các thẻ chủ đề (`allIds`).
- **Tạo, sửa, xóa theo ngữ cảnh:**
  - Khi đang lọc theo một chủ đề: thao tác xóa chỉ xóa bản ghi trong chủ đề đó.
  - Khi xem tất cả chủ đề: thao tác xóa sẽ xóa trên toàn bộ các chủ đề trong nhóm (`allIds`); thao tác sửa nội dung văn bản cập nhật đồng bộ toàn nhóm để giữ tính nhất quán.
- **Trang chi tiết từ vựng (`/vocabulary/{id}`):** Hiển thị đầy đủ chữ Nhật, cách đọc, Romaji, nghĩa tiếng Việt, danh sách mọi chủ đề chứa từ, ngày tạo/cập nhật, và danh sách các chữ Kanji xuất hiện trong từ (liên kết trực tiếp tới `/kanji/{id}`).
- **Thao tác hàng loạt (`Bulk Actions`):** Hỗ trợ chọn nhiều dòng (tối đa **200 ID/lần**) để **Xóa hàng loạt** (`POST /vocabulary/bulk-delete`) hoặc **Chuyển hàng loạt sang chủ đề khác** (`POST /vocabulary/bulk-move`, tự động gộp nếu từ đã tồn tại ở chủ đề đích).
- **Xuất tệp văn bản cho Quizlet & Anki (`GET /vocabulary/export-text`):** Xuất danh sách từ vựng đang xem/lọc ra định dạng `TAB` (ngăn cách bằng tab) hoặc `COLON` (`tiếng Nhật (cách đọc) : nghĩa`). Tệp đính kèm mã hóa UTF-8 có tiền tố BOM (`\uFEFF`), tên tệp cố định theo mẫu thời gian `nihon-vocabulary-yyyyMMdd-HHmm.txt`. Giới hạn tối đa **5000 dòng/lần**; trường hợp vượt quá 5000 dòng, hệ thống trả về mã lỗi **HTTP 400 Bad Request** với thông báo yêu cầu thu hẹp phạm vi tìm kiếm. Đồng thời áp dụng giới hạn tần suất theo địa chỉ IP tối đa **20 lần/phút**.

### 1.3. Quản lý Hán tự Kanji (`/kanji`)
- **Thêm, sửa, xóa Kanji:** Quản lý Hán tự đơn, âm Hán Việt, ý nghĩa tiếng Việt, âm On (Onyomi) và âm Kun (Kunyomi) gắn với các chủ đề thuộc nhánh Kanji (`kanjiSection = true`).
- **Liên kết Từ cần nhớ thống nhất:** Toàn bộ từ cần nhớ của Kanji được lưu trữ thống nhất trong bảng `vocabulary`. Trang chi tiết Kanji (`/kanji/{id}`) hiển thị danh sách từ cần nhớ và có nút **"Thêm từ cần nhớ"** dẫn sang `/vocabulary/new?topicId=...&japaneseText=...` (điền sẵn chủ đề và ký tự Kanji).

### 1.4. Nhập, Xuất, Lịch sử & Hoàn tác Excel (`/import`)
- **Xem trước (`POST /import/preview`) & Xác nhận (`POST /import/commit`):** Thẩm định an toàn nhiều lớp, tạo mã phiên tạm `previewToken` thời hạn 10 phút, hỗ trợ 2 chế độ `SKIP_DUPLICATES` và `UPDATE_EXISTING` trong một giao dịch nguyên tử.
- **Xuất dữ liệu (`GET /import/export`):** Kết xuất toàn bộ học liệu hiện có ra tệp `.xlsx` chuẩn gồm 2 sheet `New Word` và `Kanji` (sử dụng `SXSSFWorkbook`, tối đa 50.000 hàng/sheet, chống Formula Injection). Tệp xuất ra có thể chỉnh sửa và nhập lại trực tiếp.
- **Lịch sử kiểm toán (`GET /import/history`):** Phân trang 20 dòng/trang (mới nhất trước), hiển thị thời điểm, tên tệp, mã băm SHA-256 rút gọn 12 ký tự, số bản ghi thêm/cập nhật/bỏ qua, kết quả và địa chỉ IP. Không cho phép sửa/xóa nhật ký kiểm toán.
- **Hoàn tác lần import (`POST /import/history/{id}/undo`):** Xóa sạch từ vựng, Kanji và các chủ đề rỗng được tạo bởi một lần import (`import_batch_id`), giữ lại các chủ đề đã có dữ liệu mới thêm vào sau đó, cảnh báo số bản ghi đã chỉnh sửa sau import và ghi nhận vết hoàn tác (`undone_at` + dòng audit `UNDO`).

### 1.5. Luyện tập (`/practice`, `/practice/vi-ja`) & Thống kê (`/stats`)
- **Hai chiều luyện tập:** Kiểm tra **Nhật → Việt** (`/practice`) và **Việt → Nhật** (`/practice/vi-ja`), hỗ trợ lọc theo chủ đề bài học, chấm điểm 100% tại máy chủ.
- **Thống kê kho dữ liệu & Tiến độ phiên học (`/stats`):** Thống kê tỷ lệ từ vựng có Kanji / thuần Kana theo từng chủ đề; theo dõi lịch sử trả lời đúng/sai trong phiên làm việc (`HttpSession`, giới hạn tối đa **500 bản ghi mới nhất** thông qua `StudyProgressService.MAX_SESSION_RECORDS` để bảo vệ bộ nhớ máy chủ).

---

## 2. Yêu cầu hệ thống (System Requirements)

- **Java Development Kit (JDK):** Phiên bản **21** trở lên.
- **Apache Maven:** Phiên bản **3.9+** (hoặc sử dụng trực tiếp Maven Wrapper `./mvnw` đi kèm dự án).
- **Cơ sở dữ liệu:** **MySQL Server 8.0+** (bắt buộc bảng mã `utf8mb4` và collation `utf8mb4_unicode_ci` để hỗ trợ đầy đủ Hán tự mở rộng và Kanji tiếng Nhật).

---

## 3. Khởi tạo & Quản lý Cơ sở Dữ liệu (Database Setup)

### 3.1. Khởi tạo Cơ sở Dữ liệu mới
Chạy câu lệnh SQL sau trên MySQL Server để tạo database chuẩn Unicode:

```sql
CREATE DATABASE IF NOT EXISTS nihon_db 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
```

Cấu trúc các bảng (`topic`, `vocabulary`, `kanji_entry`, `import_audit_log`) được định nghĩa chuẩn tại [src/main/resources/schema.sql](src/main/resources/schema.sql). Khởi tạo schema bằng lệnh:

```bash
# Sử dụng mysql client
mysql -u <username> -p nihon_db < src/main/resources/schema.sql
```

*(Lưu ý: Trong môi trường `dev`, Spring Data JPA cấu hình `spring.jpa.hibernate.ddl-auto=update` sẽ tự động tạo/cập nhật cấu trúc bảng nếu database trống).*

### 3.2. Nâng cấp từ Cơ sở Dữ liệu phiên bản cũ (trước V3)
Nếu bạn đang nâng cấp từ hệ thống cũ có bảng riêng `kanji_word` sang kiến trúc đồng nhất bảng `vocabulary`, hãy chạy script SQL chuyển đổi dữ liệu sau (script an toàn `idempotent`, chỉ nạp từ chưa tồn tại):

```sql
-- 1. Chuyển đổi toàn bộ từ cần nhớ từ kanji_word sang vocabulary
INSERT INTO vocabulary (topic_id, japanese_text, reading, romaji, meaning_vi, has_kanji, source_line, created_at, updated_at)
SELECT 
    k.topic_id,
    kw.japanese,
    NULLIF(REGEXP_REPLACE(REGEXP_SUBSTR(TRIM(kw.japanese), '[（(][^()（）]+[）)]$'), '^[（(]|[）)]$', ''), ''),
    kw.romaji,
    kw.meaning_vi,
    1,
    NULL,
    NOW(),
    NOW()
FROM kanji_word kw
JOIN kanji_entry k ON kw.kanji_id = k.id
WHERE NOT EXISTS (
    SELECT 1 FROM vocabulary v 
    WHERE v.topic_id = k.topic_id AND v.japanese_text = kw.japanese
);

-- 2. Đổi tên bảng kanji_word thành bảng sao lưu an toàn
RENAME TABLE kanji_word TO kanji_word_backup_20261109;

-- 3. (Tùy chọn) Sau khi đối chiếu dữ liệu vận hành ổn định, xóa bảng backup:
-- DROP TABLE IF EXISTS kanji_word_backup_20261109;
```

---

## 4. Cấu hình Biến Môi Trường (Environment Variables)

Hệ thống sử dụng các biến môi trường để cấu hình linh hoạt mà không cần thay đổi file mã nguồn. Mẫu cấu hình được cung cấp tại [.env.example](.env.example).

| Tên biến môi trường | Mặc định (dev) | Bắt buộc (prod) | Ý nghĩa & Mô tả |
| :--- | :--- | :---: | :--- |
| `SPRING_PROFILES_ACTIVE` | `dev` | **Có** | Profile kích hoạt: `dev` (phát triển) hoặc `prod` (sản xuất). |
| `DB_URL` | `jdbc:mysql://localhost:3306/nihon_db...` | Khuyến nghị | Chuỗi kết nối JDBC MySQL đầy đủ. |
| `DB_HOST` | `localhost` | Không | Host MySQL nếu không dùng `DB_URL` trực tiếp. |
| `DB_PORT` | `3306` | Không | Cổng kết nối MySQL (mặc định: 3306). |
| `DB_NAME` | `nihon_db` | Không | Tên cơ sở dữ liệu MySQL. |
| `DB_USERNAME` | `root` | **Có** | Tên tài khoản đăng nhập MySQL. |
| `DB_PASSWORD` | *(không có)* | **Có** | Mật khẩu tài khoản MySQL (không có giá trị mặc định). |
| `DB_USE_SSL` | `false` (dev) / `true` (prod) | Không | Bắt buộc mã hóa TLS/SSL kết nối database. |
| `DB_REQUIRE_SSL` | `false` (dev) / `true` (prod) | Không | Yêu cầu máy chủ DB hỗ trợ kết nối SSL an toàn. |
| `SERVER_HTTP_PORT` | `8080` | Không | Cổng HTTP của ứng dụng máy chủ (mặc định: 8080). |
| `SERVER_SSL_PORT` | `8443` | Không | Cổng HTTPS khi bật SSL trực tiếp (mặc định: 8443). |
| `SESSION_COOKIE_SECURE`| `false` | **true** (prod) | Cờ `Secure` cho session cookie (chỉ truyền qua HTTPS). |
| `SSL_ENABLED` | `false` | Tùy chọn | Bật/tắt phục vụ chứng chỉ TLS trực tiếp từ ứng dụng. |
| `SSL_KEY_STORE_PATH` | *(rỗng)* | Tùy chọn | Đường dẫn tệp Keystore chứa chứng chỉ SSL. |
| `SSL_KEY_STORE_PASSWORD`| *(rỗng)* | Tùy chọn | Mật khẩu truy cập tệp Keystore. |
| `SSL_KEY_STORE_TYPE` | `PKCS12` | Không | Định dạng Keystore (PKCS12 hoặc JKS). |
| `SSL_KEY_ALIAS` | `tomcat` | Không | Tên định danh (alias) của chứng chỉ trong keystore. |
| `HTTPS_REDIRECT_ENABLED`| `false` (dev) / `true` (prod) | Không | Tự động chuyển hướng yêu cầu từ HTTP sang HTTPS. |
| `TRUSTED_PROXY_IPS` | IP mạng nội bộ | Không | Regex các dải IP Reverse Proxy tin cậy (Nginx/ALB). |
| `APP_IMPORT_ENABLED` | `true` | Không | Bật/tắt toàn bộ tính năng Import (`/import/**`). |
| `APP_IMPORT_ALLOWED_IPS`| Dải IP nội bộ & Private | Không | Danh sách IP / CIDR được phép thao tác Import. |
| `APP_IMPORT_TRUSTED_PROXIES`| `127.0.0.1,::1` | Không | IP các proxy nội bộ để bóc tách IP client chính xác. |
| `NIHON_IMPORT_AUTO` | `true` (dev) / `false` (prod) | Không | Tự động nạp từ vựng Markdown khi khởi động lần đầu. |

---

## 5. Hướng dẫn Khởi chạy Ứng dụng (Running the Application)

### 5.1. Môi trường Phát triển (Profile `dev`)

Chạy trực tiếp bằng Maven Wrapper (sử dụng database MySQL cục bộ):

**Trên Linux / macOS:**
```bash
export DB_PASSWORD="your_mysql_password"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Trên Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="your_mysql_password"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Ứng dụng sẽ khởi động tại địa chỉ: `http://localhost:8080`

### 5.2. Môi trường Sản xuất (Profile `prod`)

**Bước 1: Đóng gói tệp JAR sản phẩm**
```bash
./mvnw clean package -DskipTests
```
Tệp thực thi nằm tại: `target/project-nihon-0.0.1-SNAPSHOT.jar`

**Bước 2: Khởi chạy tệp JAR với biến môi trường**

**Trên Linux / macOS:**
```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL="jdbc:mysql://localhost:3306/nihon_db?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"
export DB_USERNAME="nihon_prod_user"
export DB_PASSWORD="your_strong_password"
export SESSION_COOKIE_SECURE=true

java -jar target/project-nihon-0.0.1-SNAPSHOT.jar
```

**Trên Windows (PowerShell):**
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://localhost:3306/nihon_db?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"
$env:DB_USERNAME="nihon_prod_user"
$env:DB_PASSWORD="your_strong_password"
$env:SESSION_COOKIE_SECURE="true"

java -jar target/project-nihon-0.0.1-SNAPSHOT.jar
```

*Hoặc truyền tham số dòng lệnh qua JVM Options:*
```bash
java -Dspring.profiles.active=prod \
     -Dspring.datasource.username=nihon_prod_user \
     -Dspring.datasource.password=your_strong_password \
     -jar target/project-nihon-0.0.1-SNAPSHOT.jar
```

---

## 6. Nhập Dữ liệu Học liệu Ban đầu (Curriculum Data Import)

Hệ thống hỗ trợ 2 phương thức nạp dữ liệu:

### 6.1. Phương thức 1: Nhập qua giao diện Web bằng tệp Excel (`/import`)
1. Đảm bảo IP máy thực hiện thuộc danh sách được cấp quyền trong `APP_IMPORT_ALLOWED_IPS`.
2. Truy cập: `http://localhost:8080/import`.
3. Nhấp vào liên kết **"Tải file mẫu Excel"** (hoặc truy cập `GET /import/template`) để tải tệp mẫu chuẩn `Update.xlsx`.
4. Điền dữ liệu học liệu vào 2 sheet `New Word` (từ vựng) và `Kanji` (chữ Hán & từ cần nhớ).
5. Tải tệp lên hệ thống:
   - Hệ thống hiển thị bảng **Xem trước (Preview)** các bản ghi hợp lệ và cảnh báo các bản ghi trùng lặp.
   - Chọn chế độ: **Bỏ qua trùng lặp (`SKIP_DUPLICATES`)** hoặc **Cập nhật đè (`UPDATE_EXISTING`)**.
   - Nhấn **Xác nhận nhập dữ liệu** để lưu vào cơ sở dữ liệu trong một giao dịch nguyên tử.

### 6.2. Phương thức 2: Tự động nạp từ tệp Markdown (`tu-vung-tieng-nhat-da-sap-xep.md`)
- Tệp giáo trình chuẩn [tu-vung-tieng-nhat-da-sap-xep.md](tu-vung-tieng-nhat-da-sap-xep.md) được đặt tại thư mục gốc của dự án.
- Thành phần `VocabularyDataInitializer` sẽ tự động phân tích và nạp toàn bộ danh mục chủ đề và từ vựng vào database khi khởi động nếu bảng `vocabulary` đang trống và cờ `NIHON_IMPORT_AUTO=true`.
- **Mặc định:**
  - Profile `dev`: Tự động bật (`nihon.import.auto-enabled=true`).
  - Profile `prod`: Mặc định tắt (`nihon.import.auto-enabled=false`). Khi khởi tạo hệ thống mới trên production lần đầu, bạn có thể đặt biến môi trường tạm thời:
    ```bash
    export NIHON_IMPORT_AUTO=true
    ```
    Khởi động ứng dụng một lần để nạp dữ liệu, sau đó tắt đi (`export NIHON_IMPORT_AUTO=false`) trong các lần chạy tiếp theo.

---

## 7. Hướng dẫn Kiểm thử (Testing & Verification)

Dự án có bộ kiểm thử tự động toàn diện kiểm tra tính toàn vẹn của logic nghiệp vụ, bảo mật, giới hạn trường và quy chuẩn web.

```bash
# Chạy toàn bộ quy trình kiểm thử và xác minh gói sản phẩm
./mvnw clean verify

# Chạy nhanh toàn bộ Unit & Integration tests
./mvnw test

# Kiểm thử tính đồng bộ giới hạn độ dài trường giữa DB, Entity, DTO và Excel Parser
./mvnw test -Dtest=FieldLimitsConsistencyTest

# Kiểm thử tuân thủ CSP không chứa inline script/style trên toàn bộ Thymeleaf templates
./mvnw test -Dtest=TemplateCspComplianceTest

# Kiểm thử bảo mật HTTPS, TLS redirect và Security Headers
./mvnw test -Dtest=HttpsSecurityIntegrationTest
```

---

## 8. Quy tắc Chuẩn hóa & Xác thực Dữ liệu (Validation Rules)

### 8.1. Chuẩn hóa văn bản (`com.nihon.validation.TextNormalizer`)
Trước khi kiểm tra và lưu xuống cơ sở dữ liệu, mọi trường văn bản đều được xử lý qua `TextNormalizer.normalize()`:
1. Đưa chuỗi về chuẩn Unicode dựng sẵn **NFC** (chuyển tiếng Việt tổ hợp NFD về NFC chuẩn).
2. Chuyển khoảng trắng toàn độ rộng (`U+3000` Ideographic Space) thành dấu cách ASCII thông thường (`U+0020`).
3. Loại bỏ các ký tự độ rộng bằng 0 (`U+200B`, `U+200C`, `U+200D`, `U+FEFF`).
4. Cắt khoảng trắng hai đầu và gộp nhiều khoảng trắng liên tiếp thành một dấu cách duy nhất.

### 8.2. Các bộ xác thực tùy chỉnh (Custom Bean Validation Annotations)
- **`@NoControlChars`:** Chặn tuyệt đối các ký tự điều khiển (`U+0000`–`U+001F`, `U+007F`–`U+009F`), các ký tự định dạng ẩn/đảo chiều văn bản Bidi và thẻ HTML/script.
- **`@JapaneseText`:** Yêu cầu chứa ít nhất một ký tự tiếng Nhật (`Hiragana`, `Katakana` hoặc `Han`), chỉ cho phép ký tự tiếng Nhật, chữ số, dấu câu tiếng Nhật và khoảng trắng; chặn ký tự Latin và Emoji.
- **`@KanaReading`:** Dùng cho cách đọc từ vựng và âm Kunyomi; chỉ cho phép `Hiragana`, `Katakana`, dấu trường âm `ー` và dấu phân cách.
- **`@OnyomiReading`:** Dùng cho âm Onyomi; cho phép `Katakana`, `Hiragana`, chữ cái Latin (Romaji) và các dấu phân cách đọc.
- **`@SingleKanji`:** Bắt buộc đúng **1 code point** duy nhất thuộc bảng mã Hán tự (`Character.UnicodeScript.HAN`).
- **`@HanVietText`:** Dùng cho âm Hán Việt; chỉ cho phép chữ cái tiếng Việt/Latin, khoảng trắng và dấu phẩy/chấm phẩy/gạch nối.
- **`@RomajiText`:** Chỉ cho phép chữ cái Latin, chữ số và dấu câu cơ bản; chặn chữ Nhật, chữ Việt có dấu và thẻ HTML.
- **`@VietnameseText`:** Dùng cho nghĩa tiếng Việt; yêu cầu có ít nhất một chữ cái hoặc chữ số, không chứa ký tự điều khiển hay Emoji.
- **Chống Mass Assignment:** Mọi Form Controller (`TopicController`, `VocabularyController`, `KanjiController`) đều cấu hình `@InitBinder` với `binder.setDisallowedFields("id")` để chặn client tự gán trường `id`.

### 8.3. Giới hạn độ dài các trường (`KanjiLimits`)
Toàn bộ giới hạn độ dài giữa Database (`schema.sql`), JPA Entity (`@Column`), bộ phân tích Excel (`WordSheetParser`, `KanjiSheetParser`, `VocabularyFileParser`) và các Form DTO đều dùng chung hằng số tại `com.nihon.validation.KanjiLimits` và được kiểm thử tự động bằng Reflection tại `FieldLimitsConsistencyTest`:
- Tên chủ đề (`MAX_TOPIC_LENGTH`): **255** ký tự.
- Ký tự Kanji (`MAX_KANJI_CHAR_LENGTH`): cột DB **8** ký tự (kiểm tra nghiệp vụ đúng **1** Hán tự).
- Âm Hán Việt, Ý nghĩa Kanji, Âm On, Âm Kun: **255** ký tự.
- Từ tiếng Nhật (`MAX_JAPANESE_LENGTH`), Cách đọc (`MAX_READING_LENGTH`), Romaji (`MAX_ROMAJI_LENGTH`): **255** ký tự.
- Nghĩa tiếng Việt của từ vựng / từ cần nhớ (`MAX_VOCAB_MEANING_LENGTH`): **500** ký tự.

---

## 9. Thư mục Tài liệu Kỹ thuật Chi tiết (Documentation Directory)

Dự án cung cấp tài liệu kỹ thuật chi tiết tại thư mục [docs/](docs/):
- **[docs/FIELD_LIMITS.md](docs/FIELD_LIMITS.md):** Bảng đối chiếu chi tiết toàn bộ độ dài trường giữa Database, Entity, Parser và Form DTO.
- **[docs/SECURITY_IMPORT.md](docs/SECURITY_IMPORT.md):** Báo cáo đối chiếu an toàn hệ thống theo tiêu chuẩn an ninh thông tin **OWASP Top 10 (2021)** và **PCI-DSS v4.0**.
- **[docs/HTTPS_DEV_GUIDE.md](docs/HTTPS_DEV_GUIDE.md):** Hướng dẫn tạo chứng chỉ số tự ký (PKCS12) và kích hoạt TLS/HTTPS trên môi trường cục bộ để kiểm thử chuyển hướng an toàn.

---

## 10. Giấy phép dữ liệu (Data Licenses)

- **Dữ liệu thứ tự nét viết Kanji:** Được trích xuất và tham chiếu từ dự án mã nguồn mở [KanjiVG](https://kanjivg.tagaini.net/) (bản quyền © 2009-2023 Ulrich Apel và các cộng tác viên KanjiVG).
- **Điều khoản giấy phép:** Phát hành theo giấy phép [Creative Commons Attribution-ShareAlike 3.0 Unported (CC BY-SA 3.0)](https://creativecommons.org/licenses/by-sa/3.0/). Thông tin chi tiết giấy phép lưu tại [src/main/resources/static/kanjivg/LICENSE.txt](src/main/resources/static/kanjivg/LICENSE.txt).

