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

## 2. Quy tắc chuẩn hóa & Xác thực dữ liệu (Validation Rules)

### 2.1. Chuẩn hóa văn bản (`com.nihon.validation.TextNormalizer`)
Trước khi kiểm tra và lưu xuống cơ sở dữ liệu, mọi trường văn bản đều được xử lý qua `TextNormalizer.normalize()`:
1. Đưa chuỗi về chuẩn Unicode dựng sẵn **NFC** (chuyển tiếng Việt tổ hợp NFD về NFC chuẩn).
2. Chuyển khoảng trắng toàn độ rộng (`U+3000` Ideographic Space) thành dấu cách ASCII thông thường (`U+0020`).
3. Loại bỏ các ký tự độ rộng bằng 0 (`U+200B`, `U+200C`, `U+200D`, `U+FEFF`).
4. Cắt khoảng trắng hai đầu và gộp nhiều khoảng trắng liên tiếp thành một dấu cách duy nhất.

### 2.2. Các bộ xác thực tùy chỉnh (Custom Bean Validation Annotations)
- **`@NoControlChars`:** Chặn tuyệt đối các ký tự điều khiển (`U+0000`–`U+001F`, `U+007F`–`U+009F`, bao gồm cả `\r`, `\n`, `\t`), các ký tự định dạng ẩn/đảo chiều văn bản Bidi (`U+200E`, `U+200F`, `U+202A`–`U+202E`, `U+2066`–`U+2069`) và thẻ HTML/script.
- **`@JapaneseText`:** Yêu cầu chứa ít nhất một ký tự tiếng Nhật (`Hiragana`, `Katakana` hoặc `Han`), chỉ cho phép ký tự tiếng Nhật, chữ số, dấu câu tiếng Nhật (`（`, `）`, `〜`, `・`, `ー`, `,`, `/`) và khoảng trắng; chặn ký tự Latin và Emoji.
- **`@KanaReading`:** Dùng cho cách đọc từ vựng và âm Kunyomi; chỉ cho phép `Hiragana`, `Katakana`, dấu trường âm `ー` và dấu phân cách (`.`, `-`, `,`, `/`, `、`, `・`, ngoặc tròn).
- **`@OnyomiReading`:** Dùng cho âm Onyomi; cho phép `Katakana`, `Hiragana`, chữ cái Latin (Romaji) và các dấu phân cách đọc.
- **`@SingleKanji`:** Bắt buộc đúng **1 code point** duy nhất thuộc bảng mã Hán tự (`Character.UnicodeScript.HAN`).
- **`@HanVietText`:** Dùng cho âm Hán Việt; chỉ cho phép chữ cái tiếng Việt/Latin, khoảng trắng và dấu phẩy/chấm phẩy/gạch nối.
- **`@RomajiText`:** Chỉ cho phép chữ cái Latin (kể cả nguyên âm dài `ā, ī, ū, ē, ō`), chữ số và dấu câu cơ bản; chặn chữ Nhật, chữ Việt có dấu và thẻ HTML.
- **`@VietnameseText`:** Dùng cho nghĩa tiếng Việt; yêu cầu có ít nhất một chữ cái hoặc chữ số, không chứa ký tự điều khiển hay Emoji.
- **Chống Mass Assignment:** Mọi Form Controller (`TopicController`, `VocabularyController`, `KanjiController`) đều cấu hình `@InitBinder` với `binder.setDisallowedFields("id")` để chặn client tự gán trường `id`.

### 2.3. Giới hạn độ dài các trường (`KanjiLimits`)
Toàn bộ giới hạn độ dài giữa Database (`schema.sql` / MySQL migrations), JPA Entity (`@Column`), bộ phân tích Excel (`WordSheetParser`, `KanjiSheetParser`, `VocabularyFileParser`) và các Form DTO đều dùng chung hằng số tại `com.nihon.validation.KanjiLimits` và được kiểm thử tự động bằng Reflection tại `FieldLimitsConsistencyTest`:

- Tên chủ đề (`MAX_TOPIC_LENGTH`): **255** ký tự.
- Ký tự Kanji (`MAX_KANJI_CHAR_LENGTH`): cột DB **8** ký tự (kiểm tra nghiệp vụ đúng **1** Hán tự).
- Âm Hán Việt, Ý nghĩa Kanji, Âm On, Âm Kun: **255** ký tự.
- Từ tiếng Nhật (`MAX_JAPANESE_LENGTH`), Cách đọc (`MAX_READING_LENGTH`), Romaji (`MAX_ROMAJI_LENGTH`): **255** ký tự.
- Nghĩa tiếng Việt của từ vựng / từ cần nhớ (`MAX_VOCAB_MEANING_LENGTH`): **500** ký tự.

👉 **Xem bảng đối chiếu chi tiết tại:** [docs/FIELD_LIMITS.md](docs/FIELD_LIMITS.md)

---

## 3. Hướng dẫn Nhập (Import), Xuất (Export) & Hoàn tác (Undo) Excel

### 3.1. Định dạng tệp tin & Quy chuẩn an toàn
- **Định dạng cho phép:** Chỉ chấp nhận `.xlsx` (OpenXML, Magic Bytes `50 4B 03 04`), dung lượng tối đa **5 MB**, tối đa **5.000 hàng** và **20 cột** mỗi sheet.
- **Quy tắc bảo mật tệp tải lên:**
  - Từ chối `.xls`, `.xlsm`, `.xltm`, `.xlsb` hoặc tệp chứa Macro (`vbaProject.bin`).
  - Từ chối tệp chứa công thức Excel (`CellType.FORMULA` hoặc chuỗi bắt đầu bằng `=`, `+`, `-`, `@`) để chống **Formula Injection**.
  - Từ chối tệp chứa chuỗi 13–19 chữ số thỏa mãn thuật toán Luhn (dữ liệu nghi vấn số thẻ thanh toán PAN theo chuẩn PCI-DSS).

### 3.2. Cấu trúc hai Sheet trong tệp Excel

#### A. Sheet `New Word` (Từ vựng thuộc các chủ đề thường)
- **Hàng 1 (Header):**
  | Cột A | Cột B | Cột C | Cột D |
  | :--- | :--- | :--- | :--- |
  | **Topic** | **Tiếng Nhật** | **Romaji** | **Nghĩa tiếng Việt** |
- **Dữ liệu (Từ Hàng 2):** Cột A ghi tên chủ đề ở hàng đầu tiên của chủ đề (các hàng tiếp theo cùng chủ đề có thể để trống Cột A để kế thừa). Cột B hỗ trợ định dạng kèm cách đọc trong ngoặc ở cuối chuỗi, ví dụ: `先生（せんせい）`.

#### B. Sheet `Kanji` (Chữ Hán & Từ cần nhớ)
- **Hàng 1 & Hàng 2 (Header 2 tầng, ô `G1:I1` gộp):**
  | Hàng | Cột A | Cột B | Cột C | Cột D | Cột E | Cột F | Cột G | Cột H | Cột I |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | **Hàng 1** | Topic | Kanji | Hán Việt | Ý nghĩa | On | Kun | **Từ cần nhớ / Word** *(Merge G1:I1)* | | |
  | **Hàng 2** | *(trống)* | *(trống)* | *(trống)* | *(trống)* | *(trống)* | *(trống)* | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
- **Dữ liệu (Từ Hàng 3):** Hàng có Cột B chứa 1 ký tự Kanji sẽ định nghĩa Kanji mới; các hàng tiếp theo có Cột B để trống và Cột G, H, I có dữ liệu sẽ được nhập làm từ vựng cần nhớ thuộc chủ đề của Kanji đó.

### 3.3. Cách Xuất dữ liệu (`GET /import/export`)
- Tại trang `/import`, nhấn nút **"Xuất toàn bộ ra Excel (.xlsx)"** (hoặc truy cập `GET /import/export`).
- Hệ thống tải về tệp `nihon-export-yyyyMMdd-HHmm.xlsx` gồm đủ 2 sheet `New Word` và `Kanji` đúng cấu trúc chuẩn.
- Mọi ô văn bản nếu bắt đầu bằng `=`, `+`, `-`, `@`, `\t`, `\r`, `\n` đều được tự động thêm tiền tố dấu nháy đơn (`'`) để chống Formula Injection khi mở bằng Microsoft Excel. Khi nhập lại tệp đã xuất vào hệ thống, tiền tố bảo vệ này được xử lý tương thích để bảo toàn dữ liệu gốc.

### 3.4. Cách Xem lịch sử & Hoàn tác (`GET /import/history` & `POST /import/history/{id}/undo`)
- Truy cập `/import/history` từ liên kết **"Lịch sử import"** trên trang `/import`.
- Với mỗi lần import có kết quả `SUCCESS` và chưa hoàn tác (`undone_at == null`), giao diện hiển thị nút **"Hoàn tác"** kèm hộp thoại xác nhận nêu rõ số lượng từ vựng, Kanji và chủ đề sẽ bị xóa.
- Khi xác nhận hoàn tác, hệ thống xóa toàn bộ bản ghi thuộc `import_batch_id` đó trong một giao dịch duy nhất, giữ lại các chủ đề nếu đã có dữ liệu mới thêm vào sau đợt import đó, và ghi nhận dòng nhật ký `UNDO`.

---

## 4. Cấu hình hệ thống & Bảo mật

```properties
# Giới hạn kích thước tệp tải lên (Mặc định 5 MB)
nihon.upload.excel.max-file-size-bytes=5242880
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB

# Giới hạn số hàng và cột tối đa mỗi sheet khi import
nihon.upload.excel.max-rows-per-sheet=5000
nihon.upload.excel.max-columns-per-sheet=20

# Kiểm soát an ninh mạng cho đường dẫn /import/**
app.import.enabled=true
app.import.allowed-ips=127.0.0.1,::1,0:0:0:0:0:0:0:1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7,fe80::/10
```

- **Bảo vệ CSRF (`CsrfInterceptor`):** Áp dụng cơ chế Double-Submit Cookie kết hợp Session token (`CSRF-TOKEN` / `_csrf`) với phép so sánh hằng số thời gian (`MessageDigest.isEqual`) cho mọi phương thức `POST`, `PUT`, `DELETE`, `PATCH`.
- **Kiểm soát IP mạng (`ImportSecurityInterceptor`):** Bảo vệ toàn bộ đường dẫn `/import/**`. Khi `app.import.enabled=false` trả về `404 Not Found`; khi IP ngoài danh sách `allowed-ips` trả về `403 Forbidden`.
- **Chính sách bảo mật nội dung (`Content-Security-Policy`):** Nghiêm cấm hoàn toàn inline script và inline style (`script-src 'self'; style-src 'self'`), kiểm chứng tự động qua `TemplateCspComplianceTest`.

---

## 5. Hướng dẫn chạy kiểm thử (Testing)

```bash
# Chạy toàn bộ bộ kiểm thử tự động
./mvnw test

# Chạy kiểm thử tuân thủ CSP trên toàn bộ template HTML
./mvnw test -Dtest=TemplateCspComplianceTest

# Chạy kiểm thử đồng bộ giới hạn độ dài trường giữa Parser, KanjiLimits và Entity
./mvnw test -Dtest=FieldLimitsConsistencyTest
```

---

## 6. Lưu ý kiến trúc cơ sở dữ liệu: Bảng lưu trữ sao lưu `kanji_word_backup_20261109`
- **Kiến trúc dữ liệu đồng nhất:** Toàn bộ từ vựng (bao gồm các từ cần nhớ trong sheet Kanji) đã được kiểm chứng và lưu trữ tập trung tại một nguồn dữ liệu duy nhất là bảng từ vựng chung `vocabulary`, liên kết trực tiếp với chủ đề bài học tương ứng của Kanji. Các lớp cũ (`KanjiWord`, `KanjiWordRepository`, `KanjiWordMigrationService`, `KanjiWordMigrationRunner`) đã được gỡ bỏ hoàn toàn khỏi mã nguồn.
- **Đổi tên bảng sang bản sao lưu:** Thay vì xóa ngay lập tức, migration `V7__rename_kanji_word_to_backup.sql` đã đổi tên bảng `kanji_word` thành `kanji_word_backup_20261109` để bảo toàn dữ liệu lịch sử trong giai đoạn theo dõi an toàn.
- **Cách xóa hẳn bảng sao lưu sau một thời gian vận hành ổn định:**
  ```sql
  -- 1. Kiểm tra lại lần cuối không còn dòng nào trong bảng backup bị thiếu ở bảng vocabulary:
  SELECT COUNT(*) AS missing_rows
  FROM kanji_word_backup_20261109 kw
  JOIN kanji_entry ke ON kw.kanji_id = ke.id
  LEFT JOIN vocabulary v ON v.topic_id = ke.topic_id AND v.japanese_text = kw.japanese
  WHERE v.id IS NULL;

  -- 2. Nếu missing_rows = 0, xóa hẳn bảng sao lưu để giải phóng dung lượng:
  DROP TABLE IF EXISTS kanji_word_backup_20261109;
  ```
- **Lưu ý hiệu năng phân trang (`VocabularyService.search()`):** Hiện tại `VocabularyService.search()` đang nạp các bản ghi bằng `findAllWithTopic()` rồi gom nhóm trùng lặp trong bộ nhớ Java (có ghi log cảnh báo khi vượt 20.000 bản ghi). Cần chuyển sang truy vấn phân trang trực tiếp ở cơ sở dữ liệu (DB-level pagination) khi dữ liệu lớn.
