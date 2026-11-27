# Project Nihon - Hệ Thống Quản Lý & Luyện Tập Từ Vựng, Kanji Tiếng Nhật

## 1. Giới thiệu tổng quan & Danh sách chức năng

**Project Nihon** là ứng dụng web quản lý học liệu và luyện tập tiếng Nhật (Từ vựng & Hán tự Kanji) được xây dựng trên nền tảng **Java 21**, **Spring Boot 3.4**, **Spring Data JPA**, **Thymeleaf** và **Apache POI**. Hệ thống tuân thủ chặt chẽ các nguyên tắc bảo mật **OWASP Top 10** và **PCI-DSS v4.0**.

> [!IMPORTANT]
> **Mô hình định danh & Xác thực hạ tầng:**
> Ứng dụng hiện tại không lưu tài khoản người dùng cá nhân; các chức năng quản trị nội dung (tạo, sửa, xóa tại `/topics`, `/vocabulary`, `/kanji`) mở cho người dùng ứng dụng và được bảo vệ nghiêm ngặt chống tấn công CSRF thông qua `CsrfInterceptor`. Khi triển khai ra môi trường công khai (Production), bắt buộc đặt ứng dụng phía sau lớp xác thực của hạ tầng (Reverse Proxy, API Gateway, Cloudflare Access hoặc VPN nội bộ) để bảo vệ các tuyến đường quản trị nội dung.

### 1.1. Quản lý Chủ đề (`/topics`)
- **Cây chủ đề 2 cấp:** Chỉ cho phép chủ đề gốc (`parent == null`) làm cha của chủ đề con; chặn vòng lặp và chặn chuyển chủ đề đang có con thành chủ đề con.
- **Đồng bộ nhánh Kanji (`kanjiSection`):** Chủ đề con luôn kế thừa trạng thái `kanjiSection` của chủ đề gốc; khi đổi `kanjiSection` ở chủ đề gốc, toàn bộ chủ đề con được cập nhật đồng bộ trong cùng một giao dịch.
- **Thao tác quản lý:** Tạo mới, chỉnh sửa, di chuyển thứ tự lên/xuống (`UP`/`DOWN`) trong cùng cấp cha, xem trang chi tiết (`/topics/{id}`), chuyển toàn bộ nội dung (từ vựng, Kanji, chủ đề con) sang chủ đề khác (`/topics/{id}/move-content`) rồi xóa chủ đề rỗng.

### 1.2. Quản lý Từ vựng (`/vocabulary`)
- **Hiển thị gộp nhóm thông minh (`VocabularyRowDto`):** Các bản ghi từ vựng trùng `japaneseText` ở nhiều chủ đề được gộp thành một dòng hiển thị kèm danh sách các thẻ chủ đề (`allIds`).
- **Tạo, sửa, xóa theo ngữ cảnh:**
  - Khi đang lọc theo một chủ đề: thao tác xóa chỉ xóa bản ghi trong chủ đề đó.
  - Khi xem tất cả chủ đề: thao tác xóa sẽ xóa trên toàn bộ các chủ đề trong nhóm (`allIds`); thao tác sửa nội dung văn bản cập nhật đồng bộ toàn nhóm để giữ tính nhất quán.
- **Trang chi tiết từ vựng (`/vocabulary/{id}`):** Hiển thị đầy đủ chữ Nhật, cách đọc, Romaji, nghĩa tiếng Việt, danh sách mọi chủ đề chứa từ, ngày tạo/cập nhật, và danh sách các chữ Kanji cấu thành từ vựng (liên kết trực tiếp tới `/kanji/{id}` kèm âm Hán Việt và nghĩa ngắn).
- **Thao tác hàng loạt (`Bulk Actions`):** Hỗ trợ chọn nhiều dòng (tối đa **200 ID/lần**) để **Xóa hàng loạt** (`POST /vocabulary/bulk-delete`) hoặc **Chuyển hàng loạt sang chủ đề khác** (`POST /vocabulary/bulk-move`, tự động gộp nếu từ đã tồn tại ở chủ đề đích).
- **Xuất tệp văn bản cho Quizlet & Anki (`GET /vocabulary/export-text`):** Xuất danh sách từ vựng đang xem/lọc ra định dạng `TAB` (ngăn cách bằng tab) hoặc `COLON` (`tiếng Nhật (cách đọc) : nghĩa`). Tệp đính kèm mã hóa UTF-8 có tiền tố BOM (`\uFEFF`), tên tệp cố định theo mẫu thời gian `nihon-vocabulary-yyyyMMdd-HHmm.txt`. Giới hạn tối đa **5000 dòng/lần**; áp dụng rate limit tối đa **20 lần/phút/IP**.
- **Phát âm giọng đọc tiếng Nhật tự nhiên (Web Speech API):** Tích hợp nút loa 🔊 nghe phát âm tiếng Nhật giọng Tokyo (`ja-JP`) trên bảng danh sách và trang chi tiết.

### 1.3. Quản lý Hán tự Kanji (`/kanji`)
- **Thêm, sửa, xóa Kanji:** Quản lý Hán tự đơn, âm Hán Việt, ý nghĩa tiếng Việt, âm On (Onyomi) và âm Kun (Kunyomi) gắn với các chủ đề thuộc nhánh Kanji (`kanjiSection = true`).
- **Liên kết Từ cần nhớ & Từ chứa chữ Hán:**
  - Danh sách từ cần nhớ khai báo trực tiếp trong bảng `vocabulary`.
  - Mục **"Các từ chứa chữ này"**: Tự động truy vấn phân trang (tối đa 20 từ/trang) tất cả từ vựng trong hệ thống có chứa ký tự Kanji tương ứng.
- **Trình phát thứ tự nét viết chữ Hán (KanjiVG Stroke Order Player):**
  - Khung xem thứ tự nét viết chuẩn vector SVG được nhúng an toàn phía client.
  - Các nút điều khiển: **▶ Phát hoạt ảnh nét**, **↻ Phát lại**, **🔢 Hiện/ẩn số thứ tự nét**.
  - Dữ liệu nét vector từ dự án mở [KanjiVG](https://kanjivg.tagaini.net/) (giấy phép CC BY-SA 3.0).

### 1.4. Bật/Tắt Furigana Toàn Hệ Thống (Global Furigana Toggle)
- Nút chuyển trạng thái **Furigana** trên thanh điều hướng chung (`navbar`) ở mọi trang.
- Tự động hiển thị cách đọc Hiragana phía trên từ tiếng Nhật thông qua thẻ HTML5 `<ruby>` và `<rt>`.
- Xử lý thông minh: tự động tách phần đọc trong ngoặc tròn nguồn (ví dụ: `私（わたし）` $\rightarrow$ chữ `私` kèm ruby `わたし`, không trùng lặp).
- Lưu trạng thái bật/tắt vào `localStorage` của trình duyệt có bọc `try/catch` an toàn, mặc định bật. Khi tắt: gắn lớp `.hide-furigana` để ẩn toàn bộ ruby mà không làm vỡ cấu trúc bố cục trang.

### 1.5. Thẻ Học Từ Vựng Flashcard 3D (`/flashcards`)
- Màn hình học thẻ lật trực quan:
  - Lật thẻ hai mặt Nhật ⇄ Việt bằng cách nhấn vào thẻ hoặc bấm phím **Space**.
  - Chuyển thẻ tiếp theo / quay lại bằng phím **Mũi tên Trái / Phải**.
  - Nút **Xáo bài (Shuffle)** tạo seed ngẫu nhiên ổn định trong phiên `HttpSession`, giúp đổi trang không bị trùng thẻ.
  - Tự động bỏ qua các từ thiếu nghĩa tiếng Việt và thông báo số lượng từ bị bỏ qua.
  - Tích hợp nút loa phát âm Web Speech API và chế độ hỗ trợ người nhạy cảm chuyển động (`prefers-reduced-motion`).

### 1.6. Luyện Tập Phản Xạ & Ôn Lại Từ Làm Sai (`/practice`, `/practice/vi-ja`)
- **Hai chiều tự luận gõ phím:**
  - **Nhật → Việt (`/practice`):** Nhìn từ tiếng Nhật, gõ cách đọc Hiragana (đối với từ có Kanji) và nghĩa tiếng Việt. Chấm điểm chuẩn hóa NFC, chấp nhận các nghĩa tương đương khai báo trong nguồn.
  - **Việt → Nhật (`/practice/vi-ja`):** Nhìn nghĩa tiếng Việt, gõ đúng cách viết tiếng Nhật (Kanji/Kana).
- **Chế độ Ôn lại từ làm sai (Retry Wrong Words Mode):**
  - Nút **"Ôn lại từ làm sai (N)"** tại trang chọn chủ đề, trang kết quả và trang thống kê ($N$ là số từ đang ở trạng thái sai). Nút tự động bị khóa khi $N = 0$.
  - Khi bật chế độ ôn lại (`retryWrongOnly=true`): hệ thống chỉ chọn các từ vựng mà lần làm bài gần nhất của người học là sai. Từ nào được trả lời đúng ở lần sau sẽ tự động ra khỏi tập.
  - Khi hoàn thành tất cả các từ làm sai, hệ thống hiển thị banner chúc mừng 🎉 và nút dẫn quay lại trang luyện tập.

### 1.7. Luyện Tập Trắc Nghiệm 4 Lựa Chọn (`/practice/choice`)
- **Hai hướng câu hỏi:** Nhật → Việt (`JA_VI`) và Việt → Nhật (`VI_JA`).
- Hệ thống tự động tạo 3 đáp án nhiễu từ cùng chủ đề (hoặc toàn bộ kho từ vựng), loại trừ hoàn toàn đáp án trùng nhau hoặc trùng đáp án đúng.
- Hỗ trợ phím tắt số **`1`**, **`2`**, **`3`**, **`4`** để chọn đáp án nhanh và phím **Enter** để chuyển câu tiếp theo.
- Bảo mật phiên làm bài 100% tại máy chủ (`HttpSession`): không gửi đáp án đúng xuống client trước khi nộp bài, chống gian lận qua F12/Inspect.

### 1.8. Nhập, Xuất, Lịch sử & Hoàn tác Excel (`/import`)
- **Xem trước (`POST /import/preview`) & Xác nhận (`POST /import/commit`):** Thẩm định an toàn nhiều lớp, tạo mã phiên tạm `previewToken` thời hạn 10 phút, hỗ trợ 2 chế độ `SKIP_DUPLICATES` và `UPDATE_EXISTING` trong một giao dịch nguyên tử.
- **Xuất dữ liệu (`GET /import/export`):** Kết xuất toàn bộ học liệu hiện có ra tệp `.xlsx` chuẩn gồm 2 sheet `New Word` và `Kanji` (sử dụng `SXSSFWorkbook`, tối đa 50.000 hàng/sheet, chống Formula Injection). Tệp xuất ra có thể chỉnh sửa và nhập lại trực tiếp.
- **Lịch sử kiểm toán (`GET /import/history`):** Phân trang 20 dòng/trang (mới nhất trước), hiển thị thời điểm, tên tệp, mã băm SHA-256 rút gọn 12 ký tự, số bản ghi thêm/cập nhật/bỏ qua, kết quả và địa chỉ IP. Không cho phép sửa/xóa nhật ký kiểm toán.
- **Hoàn tác lần import (`POST /import/history/{id}/undo`):** Xóa sạch từ vựng, Kanji và các chủ đề rỗng được tạo bởi một lần import (`import_batch_id`), giữ lại các chủ đề đã có dữ liệu mới thêm vào sau đó, cảnh báo số bản ghi đã chỉnh sửa sau import và ghi nhận vết hoàn tác (`undone_at` + dòng audit `UNDO`).

### 1.9. Thống Kê Tiến Độ Học Tập & Kho Dữ Liệu (`/stats`)
- **Thống kê kho dữ liệu:** Tổng số từ vựng, chủ đề, Kanji, tỷ lệ phần trăm từ có Kanji / thuần Kana theo từng chủ đề.
- **Tiến độ học tập phiên làm việc (`HttpSession`):** Thống kê số câu đã làm, số câu đúng/sai, tỷ lệ chính xác %, lọc theo chế độ (`JA_VI`, `VI_JA`, `MC_JA_VI`, `MC_VI_JA`, `ALL`) và chủ đề bài học.
- Bảng danh sách câu làm sai cần ôn tập kèm nút **"Ôn lại từ làm sai (N)"**.
- Giới hạn tối đa **500 bản ghi mới nhất** trong session để bảo vệ tài nguyên máy chủ. Có nút **Xóa tiến độ phiên** để người học bắt đầu lại từ đầu.

### 1.10. Quản lý Động từ & Phòng Biến đổi Thể (`/verbs`, `/verbs/lab`, `/verbs/practice`)
- **Kho động từ phân loại 3 nhóm (Godan, Ichidan, Kuru/Suru):** Tra cứu từ điển, thể Masu, dạng viết Kanji/Hiragana, nghĩa tiếng Việt và chủ đề bài học.
- **Phòng biến đổi thể (Conjugation Lab - `/verbs/lab`):**
  - Người học nhập động từ bất kỳ ở dạng từ điển (hoặc chọn từ có sẵn).
  - Động cơ suy luận quy tắc ngữ pháp `VerbConjugator` tự động sinh toàn bộ các thể: Thể Te (`て`), Thể Ru/Từ điển (`る`), Thể Ta/Quá khứ (`た`), Thể Nai/Phủ định (`ない`), Thể Masu (`ます`), cùng các dạng biến thể mở rộng (Khả năng, Sai khiến, Bị động, Điều kiện, Ý chí, Mệnh lệnh).
  - Hiển thị trực quan bảng chia thể kèm quy tắc ngữ pháp ngắn gọn theo từng nhóm.
- **Luyện chia thể tự luận & trắc nghiệm 4 lựa chọn (`/verbs/practice`):**
  - Tự luận: Hiển thị động từ ở thể Masu và nghĩa, người học gõ thể đích (hỗ trợ cả chữ Hán lẫn Hiragana). Máy chủ chuẩn hóa NFC, loại bỏ khoảng trắng và hiển thị giải thích quy tắc khi người học chia sai.
  - Trắc nghiệm: Sinh 4 lựa chọn với các đáp án nhiễu phổ biến (áp dụng nhầm quy tắc nhóm khác hoặc ngoại lệ), chống trùng lặp.
- **Xuất tệp văn bản Quizlet/Anki cho Động từ (`GET /verbs/export-text`):** Lọc theo thể đích, nhóm động từ, chủ đề; áp dụng giới hạn rate limit 20 lần/phút/IP và tối đa 5000 dòng.

### 1.11. Quản lý Đề thi, Làm bài & Chấm điểm Tự động (`/exams`, `/exams/admin`)
- **Mô hình cấu trúc đề thi đa cấp:**
  - `ExamPaper`: Mã đề độc nhất, tiêu đề, thời lượng (1–300 phút), chuyên mục (`MIDTERM`, `MOCK`, `QUICK_TEST`, `REVIEW`), chủ đề gắn kèm, trạng thái xuất bản (`published`).
  - `ExamSection`: Các phần thi theo thứ tự (`sort_order`) kèm hướng dẫn làm bài riêng biệt.
  - `ExamQuestion`: 5 loại câu hỏi đa dạng:
    1. **Trắc nghiệm (`MULTIPLE_CHOICE`):** Các lựa chọn đúng/sai (`exam_choice`).
    2. **Điền từ (`TYPING`):** Chấm khớp tập đáp án chấp nhận (`ACCEPTED`), chuẩn hóa NFKC, bỏ khoảng trắng và dấu câu cuối.
    3. **Sắp xếp câu / Ngôi sao (`ORDERING` - F43):** Sắp xếp các mảnh ngữ pháp; hỗ trợ câu "vị trí ngôi sao" chấm điểm theo mảnh đặt đúng ngôi sao.
    4. **Dịch câu (`TRANSLATION` - F44):** So khớp tương đối với câu mẫu và đáp án chấp nhận; nếu không khớp hoàn toàn, tự động tính độ tương đồng theo khoảng cách Levenshtein và hệ số Jaccard (ngưỡng 80% được điểm thưởng tương ứng).
    5. **Tự luận (`ESSAY`):** Đối soát tập từ khóa bắt buộc (`KEYWORD`), tính điểm tỷ lệ theo số từ khóa xuất hiện trong bài viết của người học.
- **Nhập hàng loạt đề thi bằng Excel (`.xlsx` đa sheet):** Hỗ trợ nạp nguyên đề thi hoàn chỉnh gồm các sheet `Paper`, `Sections`, `Questions`, `Choices`, `AnswerKeys`.
- **Giao diện làm bài thi chuyên nghiệp:**
  - Đồng hồ đếm ngược server-side giữ hạn chót, tự động nộp bài khi hết giờ.
  - Bảng điều hướng câu hỏi Palette (F46): Lưới số câu trực quan với màu sắc trạng thái (chưa làm, đã làm, đánh dấu xem lại 🚩).
  - Bật/tắt bản dịch tiếng Việt của đề bài (F47, mặc định tắt).
  - Tự động lưu bài nháp liên tục (F48) sau mỗi câu trả lời qua AJAX mà không cần tải lại trang.
  - Bảo mật tuyệt đối: Không lộ đáp án đúng hay gợi ý trong mã HTML của trang làm bài.
  - Trang kết quả chấm điểm chi tiết sau khi nộp, hiển thị điểm số, tỷ lệ phần trăm, kết quả từng câu kèm giải thích chi tiết.

### 1.12. Kho Ngữ pháp & Đấu trường Phản xạ Ngữ pháp (`/grammar`, `/grammar/admin`, `/grammar/arena`)
- **Kho kiến thức ngữ pháp & bổ sung (`grammar_topic`):** Phân loại `GRAMMAR` hoặc `SUPPLEMENT`, liên kết bài học chủ đề, lưu công thức, giải thích và ghi chú.
- **Trang học thẻ Accordion (`/grammar`):**
  - Mở rộng từng thẻ ngữ pháp kèm công thức, giải thích (được hiển thị an toàn bằng `th:text` và CSS `white-space: pre-wrap;`, loại trừ hoàn toàn `th:utext`).
  - Danh sách câu ví dụ minh họa (`grammar_example`) có phiên âm Furigana và nút nghe phát âm giọng đọc tự nhiên.
  - Đánh dấu trạng thái **"Đã học"** (F30) bền vững theo `learner_id` trong bảng `grammar_progress`, hiển thị thanh tiến độ tổng thể ở đầu trang.
  - Bài tập trắc nghiệm mini tại thẻ (F32): Làm bài và chấm điểm tức thì, không lộ cờ đúng trước khi nộp.
- **Đấu trường phản xạ ngữ pháp (`/grammar/arena` - F33):** Chế độ ôn luyện tốc độ cao với đồng hồ đếm ngược, tổng kết chuỗi câu đúng liên tiếp (streak) và lưu lịch sử học tập.
- **Nhập hàng loạt ngữ pháp bằng Excel (`.xlsx`):** Nhập đồng thời 4 sheet `Topics`, `Examples`, `Questions`, `Choices` có kiểm tra an toàn tệp và chống trùng mã.

### 1.13. Lịch sử Học tập Bền vững & Quyền Riêng tư (`/history`)
- **Định danh ẩn danh qua Cookie `nihon_learner`:**
  - Nhận diện thiết bị bằng UUID v4 ngẫu nhiên lưu trong cookie `nihon_learner` (`HttpOnly=true`, `SameSite=Lax`, `Secure` trên HTTPS, thời hạn 365 ngày).
  - Hoàn toàn không yêu cầu người dùng phải đăng ký tài khoản hay cung cấp thông tin cá nhân (Zero PII).
  - Dữ liệu học tập (`study_session`, `study_answer`) và bài thi (`exam_attempt`, `exam_attempt_answer`) được cách ly nghiêm ngặt theo `learner_id` (Cross-Learner Data Isolation).
- **Tự động dọn dẹp dữ liệu cũ (`Retention Policy`):**
  - Tự động quét và xóa sạch các buổi học cũ hơn cấu hình `app.history.retention-days` (mặc định **180 ngày**) thông qua Scheduled Task chạy định kỳ lúc 03:15 sáng.
- **Quyền tự quyết dữ liệu:** Người học có thể chủ động bấm **"Xóa toàn bộ lịch sử của tôi"** (`POST /history/delete-all`) bất cứ lúc nào.

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

---

## 9. Hướng dẫn Nhập Dữ liệu Hàng loạt bằng Excel (Bulk Excel Import)

Hệ thống hỗ trợ 3 module nhập dữ liệu Excel chuyên dụng, tất cả đều tuân thủ kiểm định an ninh nghiêm ngặt qua `ExcelUploadValidator` (chặn macro VBA, chặn XML External Entity/XXE, kiểm tra magic bytes `PK\x03\x04`, giới hạn 5MB và rate limit 10 lần/phút/IP):

### 9.1. Nhập Từ vựng & Kanji (`/import`)
- Tệp Excel gồm 2 sheet:
  - **`New Word`**: Cột `STT`, `Bài`, `Từ vựng`, `Cách đọc`, `Ý nghĩa`.
  - **`Kanji`**: Cột `STT`, `Bài`, `Chữ Hán`, `Hán Việt`, `Nghĩa`, `Âm On`, `Âm Kun`.
- Hỗ trợ 2 chế độ nạp: `Bỏ qua từ trùng` (`SKIP_DUPLICATES`) hoặc `Cập nhật từ trùng` (`UPDATE_EXISTING`).

### 9.2. Nhập Đề thi Đa phần & Đa dạng Câu hỏi (`/exams/admin/import`)
Tải tệp mẫu tại `GET /exams/admin/import/template`. Tệp `.xlsx` gồm 5 sheet liên kết:
1. **`Paper`**: 
   - `Code` (duy nhất, ví dụ `N5-MID-01`), `Title`, `Description`, `DurationMinutes` (1–300), `Category` (`MIDTERM`, `MOCK`, `QUICK_TEST`, `REVIEW`), `Published` (`TRUE`/`FALSE`).
2. **`Sections`**: 
   - `PaperCode`, `SortOrder`, `Title`, `Instructions`.
3. **`Questions`**: 
   - `PaperCode`, `SectionOrder`, `SortOrder`, `Type` (`MULTIPLE_CHOICE`, `TYPING`, `ORDERING`, `TRANSLATION`, `ESSAY`), `PromptJa`, `PromptVi`, `Points` (số thập phân, mặc định 1.0), `Explanation`.
4. **`Choices`** (cho câu trắc nghiệm): 
   - `QuestionSortOrder`, `SortOrder`, `Text`, `Correct` (`TRUE`/`FALSE`).
5. **`AnswerKeys`** (cho các loại câu hỏi khác): 
   - `QuestionSortOrder`, `AnswerText`, `Kind`:
     - `ACCEPTED`: Đáp án chấp nhận cho câu điền từ hoặc dịch câu.
     - `SAMPLE`: Câu dịch mẫu.
     - `KEYWORD`: Từ khóa bắt buộc trong bài luận tự luận.
     - `ORDER`: Thứ tự đúng của các mảnh ghép hoặc vị trí ngôi sao (`ORDER:3`).

### 9.3. Nhập Kho Ngữ pháp & Câu hỏi Trắc nghiệm Mini (`/grammar/admin/import`)
Tải tệp mẫu tại `GET /grammar/admin/import/template`. Tệp `.xlsx` gồm 4 sheet:
1. **`Topics`**: 
   - `Code` (duy nhất, ví dụ `N5-GR-01`), `Title`, `Category` (`GRAMMAR` hoặc `SUPPLEMENT`), `Formula`, `Explanation` (văn bản thuần, gạch đầu dòng `- ...`), `Notes`, `SortOrder`.
2. **`Examples`**: 
   - `TopicCode`, `SortOrder`, `Japanese` (có thể chứa Furigana dạng `漢字（かんじ）`), `Reading`, `MeaningVi`.
3. **`Questions`**: 
   - `TopicCode`, `SortOrder`, `Prompt`, `Explanation`.
4. **`Choices`**: 
   - `TopicCode`, `QuestionSortOrder`, `SortOrder`, `Text`, `Correct` (`TRUE`/`FALSE`).

---

## 10. Lịch sử Học tập Bền vững, Cookie Định danh & Quyền Riêng tư (F49)

Hệ thống áp dụng mô hình định danh **Zero PII (Không thu thập thông tin định danh cá nhân)**:

- **Cookie định danh `nihon_learner`**:
  - Tự động sinh mã UUID v4 ngẫu nhiên cho mỗi trình duyệt/thiết bị lần đầu truy cập.
  - Cấu hình an toàn: `HttpOnly = true`, `SameSite = Lax`, `Secure = true` (khi chạy HTTPS), thời hạn lưu trữ `Max-Age = 365 ngày` (31,536,000 giây), đường dẫn `Path = /`.
  - Giá trị định danh chỉ được đọc từ cookie, tuyệt đối không nhận từ tham số URL hoặc trường ẩn form để chống Session Fixation.
- **Phân tách & Cách ly Dữ liệu Học viên (Cross-Learner Data Isolation)**:
  - Toàn bộ kết quả buổi học (`study_session`, `study_answer`), bài làm thi (`exam_attempt`, `exam_attempt_answer`), và tiến độ ngữ pháp (`grammar_progress`) đều gắn chặt với `learner_id`.
  - Mọi câu truy vấn dữ liệu học tập đều có điều kiện bắt buộc `WHERE learner_id = :learnerId`. Học viên không thể xem hoặc can thiệp dữ liệu của học viên khác.
- **Cấu hình thời hạn lưu trữ (`app.history.retention-days`)**:
  - Tham số cấu hình tại `application.properties`:
    ```properties
    app.history.retention-days=180
    app.history.cleanup-cron=0 15 3 * * *
    ```
  - Tác vụ dọn dẹp chạy nền định kỳ lúc 03:15 sáng mỗi ngày sẽ tự động xóa sạch các buổi học cũ hơn 180 ngày.
- **Quyền tự quyết của người học**:
  - Người học có thể chủ động bấm **"Xóa toàn bộ lịch sử của tôi"** (`POST /history/delete-all`, được bảo vệ bởi CSRF token) để xóa sạch dữ liệu của mình khỏi máy chủ bất cứ lúc nào.

---

## 11. Kiến trúc Bảo mật & Mô hình Định danh Hạ tầng (Decoupled Identity Model)

### 11.1. Triết lý thiết kế (Design Philosophy)
Ứng dụng được thiết kế theo triết lý **Decoupled Architecture**: tách rời trải nghiệm học tập cốt lõi khỏi hệ thống quản trị người dùng phức tạp. Người học có thể truy cập, học từ vựng, luyện phản xạ và làm bài thi ngay lập tức mà không gặp bất kỳ rào cản nào về đăng ký tài khoản, xác thực email hay quên mật khẩu.

### 11.2. Bảo vệ các tuyến đường Quản trị trên Môi trường Production
Trong môi trường nội bộ hoặc phát triển cục bộ, các tuyến đường quản trị (`/topics/**`, `/vocabulary/**`, `/kanji/**`, `/verbs/admin/**`, `/exams/admin/**`, `/grammar/admin/**`, `/import/**`) được mở cho người dùng và được bảo vệ nhiều lớp:
1. **CSRF Protection:** Mọi request thay đổi trạng thái (POST) đều bắt buộc mang CSRF token hợp lệ sinh từ phiên làm việc (`CsrfInterceptor`).
2. **Rate Limiting:** Chặn tấn công từ chối dịch vụ (DoS) và brute-force thông qua bộ đếm trượt `RateLimiterService`.
3. **Mã hóa xem trước:** Mọi thao tác import Excel đều yêu cầu xác thực qua token ký ngẫu nhiên có thời hạn 10 phút.

Khi đưa hệ thống ra môi trường Internet công khai, các tuyến đường quản trị nội dung (`/topics/new`, `/topics/*/edit`, `/vocabulary/bulk-*`, `/import/**`, `*/admin/**`) cần được đặt phía sau lớp bảo vệ của **Hạ tầng mạng / Cổng xác thực (Infrastructure Authentication)**:
- **Reverse Proxy Authentication:** Sử dụng chỉ thị `auth_request` của Nginx hoặc ForwardAuth của Traefik kết nối tới hệ thống Single Sign-On (Authelia, Keycloak, Authentik).
- **Zero Trust Network Access:** Sử dụng Cloudflare Access, AWS Verified Access hoặc Google Cloud Identity-Aware Proxy (IAP) để giới hạn quyền truy cập trang quản trị chỉ cho các kỹ sư/giáo viên được cấp quyền.
- **VPN / Internal Network:** Chỉ cho phép truy cập các URL quản trị từ dải địa chỉ IP nội bộ của trường học/tổ chức.

---

## 12. Giấy phép Dữ liệu & Bản quyền (Licenses & Attribution)

- **Dữ liệu Thứ tự nét viết chữ Hán (Kanji Stroke Order):**
  - Trích xuất và tham chiếu từ dự án mã nguồn mở **[KanjiVG](https://kanjivg.tagaini.net/)**.
  - Bản quyền: © 2009–2023 Ulrich Apel và các cộng tác viên KanjiVG.
  - Giấy phép: Được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 3.0 Unported ([CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/))**.
  - Toàn văn giấy phép được lưu trữ tại [src/main/resources/static/kanjivg/LICENSE.txt](src/main/resources/static/kanjivg/LICENSE.txt).
- **Mã nguồn ứng dụng:** Phát hành cho mục đích học tập và đào tạo tiếng Nhật phi thương mại.
