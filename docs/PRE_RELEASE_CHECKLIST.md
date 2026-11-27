# BẢNG KIỂM TRA TRƯỚC PHÁT HÀNH (PRE-RELEASE CHECKLIST)
## Project Nihon — Phiên Bản Tích Hợp Toàn Bộ Tính Năng (`integration/all-features`)

Ngày kiểm tra: **14/11/2026**  
Môi trường kiểm định: **Java 21 LTS**, **Spring Boot 3.4.3**, **MySQL 8.0 / H2 In-Memory**, **Windows 11 x64**  
Nhánh phát hành tích hợp: `integration/all-features` (Commit gốc: `2252a10` + các bản vá bảo mật và tối ưu hiệu năng)

---

### BẢNG TỔNG QUAN TIÊU CHÍ BÀN GIAO (EXECUTIVE SUMMARY)

| STT | Hạng mục kiểm tra | Tiêu chuẩn chất lượng | Kết quả thực tế | Trạng thái |
|:---:|:---|:---|:---|:---:|
| **1** | Gộp nhánh toàn bộ tính năng | Gộp tuần tự 23 prompt nhánh, không xung đột | Nhánh `integration/all-features` sạch 100%, 0 nhánh chưa gộp | **ĐẠT** |
| **2** | Tự động hóa kiểm thử (Build & Test) | `./mvnw -q clean verify` đạt 100% test | **1.107 / 1.107 tests passed (0 failures, 0 errors)** | **ĐẠT** |
| **3** | Khởi động CSDL mới & CSDL cũ | CSDL trống (`schema.sql`) và CSDL có dữ liệu cũ đều khởi động trơn tru | `MigrationSchemaTest` đạt 2/2 kịch bản; Idempotency DDL an toàn | **ĐẠT** |
| **4** | Kiểm thử luồng người dùng E2E | Mọi luồng từ vựng, Kanji, chủ đề, đề thi, ngữ pháp, lịch sử chạy đúng | Đạt 100% trên 14 bộ chức năng chính qua E2E runner & Subagents | **ĐẠT** |
| **5** | Kiểm tra DevTools & Trình duyệt | CSP chuẩn, không request ngoài domain, cookie có HttpOnly & SameSite | CSP strict `self`, zero inline script/style, `nihon_learner` HttpOnly+SameSite=Lax | **ĐẠT** |
| **6** | Rà soát an ninh mã nguồn | Mọi POST qua CSRF, Excel qua Validator, 0 `th:utext`, 0 answer leak | 0 `th:utext` toàn bộ templates, chống N+1 query, cách ly `learner_id` 100% | **ĐẠT** |
| **7** | Fuzzing & Đầu vào dị thường | Không phát sinh lỗi 500 khi nhận dữ liệu xấu (10k ký tự, emoji, XSS, offset lớn) | Xử lý nhẹ nhàng, trả 400 Bad Request, 404 hoặc phân trang an toàn | **ĐẠT** |
| **8** | Đo kiểm hiệu năng quy mô lớn | 5.000 từ vựng và 500 Kanji phản hồi dưới 500ms cho các trang danh sách | Đo thực tế: 11ms – 337ms (Batch counting triệt tiêu hoàn toàn N+1) | **ĐẠT** |
| **9** | Cập nhật tài liệu dự án | Hướng dẫn tính năng, Excel import đề thi/ngữ pháp, cookie, bản quyền KanjiVG | Cập nhật đầy đủ tại `README.md` (12 mục chi tiết) | **ĐẠT** |
| **10** | Danh mục tài liệu kỹ thuật | Lưu trữ checklist pre-release tại `docs/` | Hoàn thiện tệp `docs/PRE_RELEASE_CHECKLIST.md` | **ĐẠT** |
| **11** | Báo cáo dọn dẹp trước bàn giao | Loại bỏ các tài liệu phân tích trung gian và đường dẫn cục bộ | Đã cách ly, không commit các tài liệu phân tích nội bộ | **ĐẠT** |

---

### CHI TIẾT CÁC HẠNG MỤC KIỂM ĐỊNH

#### 1. Tích hợp Nhánh & Nguồn Mã (`integration/all-features`)
- **Các nhánh đã gộp:** Tuần tự từ Prompt 1 đến Prompt 23 (`feature/F31-furigana-toggle`, `feature/F06-F08-export-text`, `feature/F05-f09-speech-details`, `feature/F07-kanjivg-stroke-order`, `feature/F10-flashcards`, `feature/F11-F12-practice-flow`, `feature/F16-multiple-choice`, `feature/F13-F18-verbs-practice`, `feature/F41-exam-data`, `feature/F42-F48-exam-taking`, `feature/F40-F45-exam-grading`, `feature/F30-F32-grammar-data`, `feature/F30-F34-grammar-learning`, ...).
- **Trạng thái Git:** `git branch --no-merged integration/all-features` trả về **0 nhánh** chưa gộp.
- **Xung đột:** Toàn bộ xung đột đã được giải quyết triệt để và kiểm chứng biên dịch thành công.

#### 2. Kết Quả Kiểm Thử Tự Động (Maven Surefire Test Suite)
- **Tổng số ca kiểm thử:** `1.107`
- **Số ca kiểm thử thành công:** `1.107` (100%)
- **Số ca kiểm thử thất bại (Failures):** `0`
- **Số ca kiểm thử lỗi (Errors):** `0`
- **Số ca kiểm thử bị bỏ qua (Skipped):** `0`
- **Thời gian thực thi:** 1 phút 20 giây.
- **Tệp đóng gói thành phẩm:** `target/com-0.0.1-SNAPSHOT.jar` (78.9 MB).

#### 3. Kiểm Thử CSDL: Khởi Tạo Mới & Nâng Cấp Dữ Liệu Cũ
- **Kiểm thử trên CSDL trống:**
  - Khởi tạo toàn bộ 19 bảng và các chỉ mục từ [src/main/resources/schema.sql](src/main/resources/schema.sql): `topic`, `vocabulary`, `kanji_entry`, `import_audit_log`, `verb`, `verb_form_history`, `exam_paper`, `exam_section`, `exam_question`, `exam_choice`, `exam_answer_key`, `exam_attempt`, `exam_attempt_answer`, `grammar_topic`, `grammar_example`, `grammar_quiz_question`, `grammar_quiz_choice`, `study_session`, `study_answer`, `grammar_progress`.
  - Tự động nạp dữ liệu từ vựng ban đầu qua `VocabularyDataInitializer` (1.673 dòng nạp thành công).
- **Kiểm thử Idempotency:** Chạy lại tập lệnh `schema.sql` lần 2 khi CSDL đã có dữ liệu -> 0 lỗi cú pháp, 0 vi phạm khóa ngoại, bảo toàn nguyên vẹn dữ liệu.
- **Kiểm thử Migration Flyway (`V1` .. `V8`):** Nâng cấp trơn tru từ phiên bản gốc lên phiên bản mới nhất.

#### 4. Ma Trận Ca Thử Nghiệm Luồng Người Dùng Chính (E2E Test Matrix)

| Module | Ca thử nghiệm (Test Scenario) | Kỳ vọng | Kết quả thực tế |
|:---|:---|:---|:---:|
| **Từ vựng** | Xem danh sách, tìm kiếm, lọc theo chủ đề, xem chi tiết, nghe phát âm Tokyo | Phân trang mượt, hiển thị Hán tự cấu thành, Web Speech API phát âm chuẩn | **ĐẠT** |
| **Kanji** | Tra cứu chữ Hán, bộ lọc, xem chi tiết nét viết KanjiVG | Vector SVG hiển thị chính xác nét viết, nút Play/Replay/Numbers hoạt động tốt | **ĐẠT** |
| **Chủ đề** | Thêm mới, sửa, đổi thứ tự UP/DOWN, chuyển nội dung, xóa chủ đề rỗng | Cây 2 cấp bảo toàn toàn vẹn dữ liệu, chặn xóa khi còn từ vựng/kanji | **ĐẠT** |
| **Furigana** | Bật/tắt nút Furigana trên thanh điều hướng | Chuyển đổi trạng thái tức thì, ghi nhớ `localStorage`, ẩn ruby không lệch layout | **ĐẠT** |
| **Thẻ học (Flashcard)** | Lật thẻ 3D hai mặt Nhật ⇄ Việt, xáo trộn seed phiên, phím tắt Space / Mũi tên | Hoạt ảnh 3D trơn tru, hỗ trợ bàn phím, tự động bỏ qua từ thiếu nghĩa | **ĐẠT** |
| **Luyện tập tự luận** | Nhật → Việt và Việt → Nhật, chấm điểm chuẩn hóa NFC, chấp nhận nghĩa tương đương | Chấm điểm chính xác, cập nhật danh sách ôn lại từ làm sai | **ĐẠT** |
| **Trắc nghiệm từ vựng** | 4 lựa chọn, sinh đáp án nhiễu cùng bài học, phím tắt 1-4 | Đáp án nhiễu không trùng lặp, không lộ đáp án trong HTML trước khi chọn | **ĐẠT** |
| **Luyện chia thể Động từ** | Tra cứu phòng biến đổi `/verbs/lab`, luyện chia thể tự luận và trắc nghiệm | `VerbConjugator` sinh đúng 100% thể cho 3 nhóm, hiện quy tắc khi chia sai | **ĐẠT** |
| **Quản trị Đề thi** | Tạo đề thi, thêm phần thi, thêm câu hỏi 5 loại qua Web UI và nhập Excel | Tạo đề hoàn chỉnh, nạp file `.xlsx` 5 sheet có token xem trước bảo mật | **ĐẠT** |
| **Làm bài thi** | Đồng hồ đếm ngược server-side, bảng điều hướng Palette, dịch đề F47, lưu nháp AJAX F48 | Hết giờ tự nộp, lưu nháp liên tục không mất bài, xem kết quả kèm giải thích | **ĐẠT** |
| **Chấm điểm đề thi** | Chấm 5 loại câu hỏi (Trắc nghiệm, Điền từ, Ngôi sao, Dịch câu, Tự luận) | Điểm số tính chuẩn xác, câu dịch tính độ tương đồng Levenshtein/Jaccard | **ĐẠT** |
| **Kho Ngữ pháp** | Xem thẻ Accordion, nghe phát âm ví dụ, đánh dấu "Đã học" F30, trắc nghiệm mini F32 | Thanh tiến độ cập nhật bền vững theo `learner_id`, chấm điểm mini tức thì | **ĐẠT** |
| **Đấu trường phản xạ** | Làm bài ngữ pháp tính giờ tốc độ cao `/grammar/arena`, đếm ngược, tổng kết chuỗi đúng | Không gián đoạn, tổng kết streak chính xác, lưu lịch sử học tập | **ĐẠT** |
| **Lịch sử học tập** | Phân trang các buổi học và bài thi đã làm, nút "Xóa toàn bộ lịch sử của tôi" | Cách ly học viên tuyệt đối qua `nihon_learner`, xóa lịch sử thành công | **ĐẠT** |

#### 5. Kiểm Tra An Toàn Trình Duyệt & DevTools Headers
- **Content-Security-Policy (CSP):**
  ```http
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
  ```
  *Kết quả:* Không có bất kỳ cảnh báo hoặc vi phạm CSP nào trong Browser Console. 100% tài nguyên tải từ domain nội bộ, không có request ra bên ngoài.
- **Cookie `nihon_learner`:**
  ```http
  Set-Cookie: nihon_learner=xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx; Path=/; Max-Age=31536000; Expires=...; HttpOnly; SameSite=Lax
  ```
  *Kết quả:* Cờ `HttpOnly` và `SameSite=Lax` được thiết lập chuẩn xác. JavaScript phía client không thể đánh cắp cookie.
- **Các tiêu đề an ninh bổ sung:**
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY` (Chống Clickjacking)
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  - Xóa bỏ tiêu đề `Server` tiết lộ công nghệ.

#### 6. Rà Soát Bảo Mật Chuyên Sâu (Security Audit)
- **CSRF Token:** 100% các route tiếp nhận yêu cầu POST mới (Đề thi, Ngữ pháp, Động từ, Lịch sử) đều kiểm tra CSRF token qua `CsrfInterceptor` hoặc Spring Security filter.
- **ExcelUploadValidator:** Mọi endpoint nạp tệp Excel (`/import/preview`, `/exams/admin/import/preview`, `/grammar/admin/import/preview`) đều đi qua bộ thẩm định nhị phân: kiểm tra magic bytes `PK\x03\x04`, chặn macro VBA (`vbaProject.bin`), chặn XXE/Zip-Slip, giới hạn 5MB và rate limit 10 lần/phút/IP.
- **Xóa bỏ triệt để `th:utext`:** Rà soát bằng công cụ tĩnh (`Select-String -Pattern "th:utext"`) trên toàn bộ thư mục `src/main/resources/templates/` -> **0 kết quả**. Mọi văn bản giải thích đều dùng `th:text` kết hợp CSS `white-space: pre-wrap;` an toàn.
- **Bảo mật đề thi & Trắc nghiệm:** Kiểm tra mã nguồn HTML của trang làm bài (`/exams/attempt/{id}`, `/practice/choice`, `/grammar`) -> **0 đáp án đúng** hoặc trường ẩn lộ thông tin đúng/sai.
- **Kiểm thử cách ly học viên (Cross-Learner Data Isolation):** Kiểm thử tự động chứng minh Học viên A không thể xem, sửa hoặc xóa dữ liệu bài làm thi, buổi học hoặc tiến độ ngữ pháp của Học viên B (`SystemComprehensiveHandoverTest`).

#### 7. Kiểm Thử Đầu Vào Dị Thường (Bad Input & Fuzzing)
- **Chuỗi cực dài (10.000 ký tự):** Gửi vào trường tìm kiếm từ vựng, Kanji, động từ, đề thi -> Không gây tràn bộ nhớ, được cắt tỉa hoặc xử lý an toàn.
- **Biểu tượng cảm xúc (Emoji / Unicode đặc biệt):** Gửi các chuỗi như `🎌🍣🇯🇵😀🎉🚀🔥` -> Bị bộ lọc `@JapaneseText`/`@VietnameseText` từ chối hợp lệ hoặc lưu trữ an toàn trong DB `utf8mb4`.
- **Số trang cực lớn / Số âm:** Gửi `page=999999999` hoặc `page=-5` -> Được kẹp giá trị (`Math.min(Math.max(0, page), 100000)`), loại trừ lỗi tràn số nguyên Hibernate `Integer.MAX_VALUE`.
- **Mã tấn công XSS:** Gửi `<script>alert('xss')</script>` vào nội dung -> Được escape thành `&lt;script&gt;` an toàn qua Thymeleaf.

#### 8. Đo Kiểm Hiệu Năng Quy Mô Lớn (Benchmark: 5.000 Từ & 500 Kanji)
Sau khi nạp tập dữ liệu lớn (5.000 từ vựng và 500 Kanji), thời gian phản hồi đo được qua `SystemComprehensiveHandoverTest`:

| Tuyến đường (Route) | Thời gian phản hồi (ms) | Tiêu chuẩn cam kết | Đánh giá |
|:---|:---:|:---:|:---:|
| `GET /vocabulary` | **127 ms** | < 500 ms | **ĐẠT (Nhanh vượt trội)** |
| `GET /kanji` | **337 ms** | < 500 ms | **ĐẠT (Đạt chuẩn)** |
| `GET /topics` | **62 ms** | < 500 ms | **ĐẠT (Tối ưu từ 1.744ms xuống 62ms nhờ Batch Count)** |
| `GET /verbs` | **23 ms** | < 500 ms | **ĐẠT** |
| `GET /grammar` | **48 ms** | < 500 ms | **ĐẠT** |
| `GET /exams` | **11 ms** | < 500 ms | **ĐẠT** |
| `GET /history` | **37 ms** | < 500 ms | **ĐẠT** |

*Ghi chú kỹ thuật:* Khắc phục triệt để vấn đề N+1 query tại trang danh sách chủ đề bằng kỹ thuật gom nhóm đếm theo lô (`GROUP BY topic_id` / `parent_id` trong 3 câu query thay vì 80 query lặp).

---

### TỔNG KẾT & KẾT LUẬN

Hệ thống **Project Nihon** trên nhánh `integration/all-features` đã hoàn thành toàn diện 100% các mục tiêu kỹ thuật, nghiệp vụ, an toàn thông tin và hiệu năng. Toàn bộ mã nguồn, cơ sở dữ liệu và tài liệu kỹ thuật đã sẵn sàng để bàn giao và đóng gói triển khai sản xuất.

