# BÁO CÁO CẬP NHẬT TÍNH NĂNG VÀ KIỂM TOÁN HỆ THỐNG - ĐỢT 5 (V5)
## DỰ ÁN: PROJECT NIHON (HỆ THỐNG HỌC TẬP VÀ QUẢN LÝ TIẾNG NHẬT)

---

- **Thời điểm kiểm toán:** 15/11/2026
- **Nhánh Git thực hiện:** `feature/update-v5-audit-report` (Hợp nhất từ `integration/all-features`)
- **Môi trường & Công nghệ:** Java 21 LTS, Spring Boot 3.4.3, Thymeleaf 3.1, Hibernate 6.6.8.Final, H2 In-Memory (Test) / MySQL 8.0 (Production), Maven 3.9
- **Tổng số ca kiểm thử tự động:** **1,194 / 1,194 passed (100%)**
- **Trạng thái kiểm toán CSP:** **100% tuân thủ CSP Strict** (0 vi phạm `onclick`, 0 script inline, 0 style inline)

---

## 1. TỔNG QUAN KIỂM TOÁN & XÁC MINH HỆ THỐNG

### 1.1. Kết quả thực thi `mvnw clean test`
Quá trình làm sạch thư mục `target` và chạy lại toàn bộ test suite từ đầu ghi nhận kết quả:
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.nihon.config.HttpsSecurityIntegrationTest
...
[INFO] Running com.nihon.controller.TopicHubIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nihon.controller.PracticeStudioControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nihon.controller.NavbarTemplateTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.nihon.config.TemplateCspComplianceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] Results:
[INFO] Tests run: 1194, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time: 02:16 min
```

### 1.2. Kết quả kiểm toán CSP (`TemplateCspComplianceTest`) & Quét mã tĩnh
- **Kiểm thử tự động:** `TemplateCspComplianceTest` quét toàn bộ 64 template HTML ngoài thư mục `fragments` và tất cả fragment. Kết quả: **PASS 100%**.
- **Quét mã tĩnh chuyên sâu:**
  - `git grep -in "onclick" src/main/resources/templates/` ➔ **0 kết quả** (Hoàn toàn không có trình xử lý `onclick` inline).
  - `git grep -inE " (on[a-zA-Z]+)=" src/main/resources/templates/` ➔ **0 kết quả** (Không có bất kỳ sự kiện inline nào: `onload`, `onchange`, `onsubmit`, `onblur`...).
  - Quét thẻ `<script>` không có thuộc tính `src` hoặc `th:src` ➔ **0 kết quả** (100% JavaScript được đặt tại các tệp tĩnh `.js` riêng biệt và nạp qua cờ `defer`).
  - Quét `style="` hoặc `th:style` ➔ **0 kết quả** (Toàn bộ điều khiển giao diện được thực hiện qua các lớp CSS và thuộc tính HTML5 chuẩn `hidden`).

---

## 2. BẢNG CHI TIẾT TỪNG TÍNH NĂNG ĐÃ THỰC HIỆN (V1 – V5)

| Mã | Tên tính năng | Trạng thái | Tệp mã nguồn đã sửa / tạo mới | Ca kiểm thử (Unit / Integration Test) | Bằng chứng kiểm tra thực tế |
| :-: | :--- | :-: | :--- | :--- | :--- |
| **F05** | Thanh điều chỉnh tốc độ phát âm TTS trên Navbar | **HOÀN THÀNH** | - `src/main/resources/templates/fragments/navbar.html`<br>- `src/main/resources/templates/fragments/speech.html`<br>- `src/main/resources/static/js/nihon-speech.js` | `NavbarTtsSpeedTest.java` (5 tests) | Select 3 mức: `1.0x`, `0.85x`, `0.75x`, mặc định `0.9x`. Lưu `localStorage nihon_tts_speed`, kiểm tra giá trị nằm trong danh sách trắng, gán vào `utterance.rate`. Giá trị lạ tự động hoàn về `0.9x`. |
| **F68** | Chấm điểm & Xếp loại S / A / B / C đề thi | **HOÀN THÀNH** | - `src/main/java/com/nihon/service/ExamGradingService.java`<br>- `src/main/resources/application.properties`<br>- `src/main/resources/templates/exams/taker/result.html` | `ExamGradingGradeTest.java` (10 tests) | Hàm `calculateGrade(BigDecimal percentage)` trả về hạng S ($\ge 90\%$), A ($\ge 80\%$), B ($\ge 65\%$), C ($< 65\%$). Kiểm thử biên chính xác: 0, 64.99, 65.00, 79.99, 80.00, 89.99, 90.00, 100.00. Hiển thị huy hiệu lúc render, không sửa bảng CSDL. |
| **F86** | Chuẩn hóa Katakana sang Hiragana (`TextNormalizer`) | **HOÀN THÀNH** | - `src/main/java/com/nihon/validation/TextNormalizer.java`<br>- `src/main/java/com/nihon/service/exam/grading/TypingGrader.java` | - `TextNormalizerTest.java`<br>- `TypingGraderTest.java` | Hàm `katakanaToHiragana` dịch mã `0x60` cho dải `U+30A1` đến `U+30F6`, giữ nguyên trường âm `U+30FC`. Tích hợp vào `TypingGrader`: chỉ áp dụng khi đáp án đúng là Hiragana. "タベル" khớp "たべる" đúng; "コーヒー" khớp "コーヒー" đúng; "こーひー" với "コーヒー" giữ nguyên hành vi. |
| **F30** | Tự xác nhận điểm dịch câu (`Self-approval`) | **HOÀN THÀNH** | - `src/main/resources/db/migration/V12__add_self_approved_to_attempt_answer.sql`<br>- `src/main/java/com/nihon/entity/ExamAttemptAnswer.java`<br>- `src/main/java/com/nihon/controller/ExamTakingController.java`<br>- `src/main/java/com/nihon/service/exam/ExamService.java`<br>- `src/main/resources/templates/exams/taker/result.html`<br>- `src/main/resources/static/js/exam-result.js` | `ExamTranslationSelfApproveIntegrationTest.java` (5 tests) | Thêm cột `self_approved BOOLEAN NOT NULL DEFAULT FALSE`. Endpoint `POST /exams/attempt/{attemptId}/answer/{answerId}/self-approve` bảo vệ bởi CSRF, kiểm tra attempt, kiểm tra cookie `learner_id`, kiểm tra đúng loại `TRANSLATION`. Cập nhật điểm tối đa, hiển thị nhãn "Tự xác nhận", tính lại tổng điểm và xếp loại, có nút hủy xác nhận. |
| **F78** | Checkbox "Tự chuyển câu" khi luyện gõ từ vựng | **HOÀN THÀNH** | - `src/main/resources/templates/practice/index.html`<br>- `src/main/resources/templates/practice/result.html`<br>- `src/main/resources/templates/practice/vi-ja-index.html`<br>- `src/main/resources/templates/practice/vi-ja-result.html`<br>- `src/main/resources/static/js/practice-next.js` | `PracticeAutoNextTest.java` (4 tests) | Checkbox trên `/practice` và `/practice/vi-ja`, lưu `localStorage nihon_auto_next`, mặc định tắt. Khi trả lời đúng và checkbox bật: đếm ngược 650 ms rồi tự động chuyển câu tiếp theo. Khi trả lời sai: dừng lại không chuyển câu. |
| **F84** | Tùy chỉnh tốc độ diễn hoạt vẽ nét chữ Hán KanjiVG | **HOÀN THÀNH** | - `src/main/resources/templates/kanji/detail.html`<br>- `src/main/resources/static/js/kanji-stroke-player.js` | `KanjiStrokeStaticResourcesTest.java` (3 tests) | 3 nút `0.5x`, `1x`, `2x` cạnh khung vẽ SVG. `kanji-stroke-player.js` chia thời gian mỗi nét cho hệ số tốc độ được chọn. Lưu cài đặt vào `localStorage nihon_kanji_speed`. Giữ nguyên phần ghi công bản quyền CC BY-SA 3.0. |
| **F95** | Rà soát quy tắc chấm thể Te phủ định | **HOÀN THÀNH** | `src/main/java/com/nihon/service/verb/VerbPracticeService.java` | `VerbPracticeServiceTest.java` | Đối với câu hỏi "Te phủ định": chỉ chấp nhận hai dạng `ないで` và `なくて`. Từ chối hoàn toàn dạng masu phủ định (`ません`) vì đây là thể ngữ pháp khác. |
| **F56** | Bảng đếm số người (~人) từ 1 đến 10 người | **HOÀN THÀNH** | - `src/main/resources/templates/fragments/counter-people.html`<br>- `src/main/resources/static/js/counter-people.js`<br>- `src/main/resources/templates/grammar/index.html` | `CounterPeopleFragmentTest.java` (4 tests) | Fragment hiển thị 10 ô đếm người: ひとり, ふたり, さんにん, よにん, ごにん, ろくにん, ななにん/しちにん, はちにん, きゅうにん, じゅうにん. Đánh dấu màu cam cho các từ bất quy tắc (1, 2, 4, 7 người). Nút bấm mang `data-speak`, sự kiện gắn qua `counter-people.js`, nhúng tại trang `templates/grammar/index.html`. |
| **F32** | Bộ dữ liệu mẫu 50 câu điền trợ từ độc quyền A2 | **HOÀN THÀNH** | - `docs/samples/particles_50.xlsx`<br>- `src/test/java/com/nihon/service/exam/Particles50SampleImportTest.java` | `Particles50SampleImportTest.java` (3 tests) | Tự biên soạn 50 câu điền trợ từ trình độ A2 (は, が, を, に, で, へ, と, から, まで), thuần tiếng Nhật không pha tạp ký tự Latin lạ, kèm giải thích ngữ pháp tiếng Việt. Đóng gói file Excel mẫu theo đúng format import của hệ thống, kiểm thử import thành công 100%, không seed trực tiếp vào CSDL để người dùng tự import. |
| **F71** | Hiệu ứng pháo giấy Canvas 2D chúc mừng điểm cao | **HOÀN THÀNH** | - `src/main/resources/static/js/confetti.js`<br>- `src/main/resources/templates/exams/taker/result.html` | `ExamResultConfettiTest.java` (4 tests) | Tự dựng hiệu ứng hạt pháo giấy bằng Canvas 2D thuần, không dùng thư viện ngoài. Kích hoạt khi điểm thi đạt $\ge 90\%$. Tôn trọng `prefers-reduced-motion` (tự động tắt hiệu ứng khi người dùng bật giảm chuyển động). Tự dừng và dọn dẹp canvas sau đúng 3 giây. Canvas có `aria-hidden="true"` và `pointer-events: none`. |
| **F12** | Trang Cẩm nang Bài học All-in-One Hub | **HOÀN THÀNH** | - `src/main/java/com/nihon/controller/TopicHubController.java`<br>- `src/main/java/com/nihon/service/TopicHubService.java`<br>- `src/main/java/com/nihon/dto/topic/TopicHubDto.java`<br>- `src/main/java/com/nihon/repository/*.java`<br>- `src/main/resources/templates/topics/hub.html`<br>- `src/main/resources/templates/topics/detail.html` | `TopicHubIntegrationTest.java` (4 tests) | Gom trên một trang: Từ vựng, Kanji, Ngữ pháp kèm ví dụ song ngữ, Động từ và bảng 5 thể, Đề thi liên quan, thanh 8 liên kết luyện tập và quiz. Tối ưu truy vấn bằng `JOIN FETCH`, Hibernate Statistics xác nhận nạp toàn bộ trang với **đúng 6 truy vấn SQL** ($\le 8$ truy vấn). Validate `topicId <= 0` hoặc không tồn tại trả về HTTP 404 NOT_FOUND. |
| **F53** | Tổ hợp Luyện gõ Tiếng Nhật Đa chế độ (Typing Studio) | **HOÀN THÀNH** | - `src/main/java/com/nihon/controller/PracticeStudioController.java`<br>- `src/main/resources/templates/practice/studio.html`<br>- `src/main/resources/static/js/practice-studio.js`<br>- `src/main/resources/templates/practice/index.html`<br>- `src/main/resources/templates/practice/vi-ja-index.html`<br>- `src/main/resources/templates/practice/kanji-index.html`<br>- `src/main/resources/templates/fragments/navbar.html` | `PracticeStudioControllerTest.java` (6 tests) | Giao diện Studio có thanh chuyển chế độ (tabs), gọi lại trực tiếp 3 màn hình `/practice`, `/practice/vi-ja`, `/practice/kanji` bằng Thymeleaf fragment (`jaViCard`, `viJaCard`, `kanjiCard`). Giữ nguyên 100% các URL cũ. Không lặp lại bất kỳ logic chấm điểm nào. Hỗ trợ chuyển tab client-side không reload qua `hidden` và fallback liên kết server-side. |

---

## 3. DANH SÁCH CÁC TÍNH NĂNG ĐÃ LOẠI BỎ (KHÔNG LÀM)

Theo định hướng kiến trúc tinh gọn, giảm tải tài nguyên và tập trung vào trải nghiệm học tập cốt lõi (Core EdTech Experience), các tính năng sau **đã được chính thức loại bỏ hoàn toàn khỏi phạm vi dự án**:

1. **Nhóm đồ họa 3D, WebGL và Three.js**:
   - `F11`: Không gian Văn hóa Nhật Bản 3D tương tác (`demo_3d`).
   - `F49`: Triển lãm Mô hình Từ vựng 3D 360° (167+ model GLTF/GLB).
   - `F50`: Tương tác Raycasting & trượt camera góc nhìn 3D.
   - `F82`: Bảng xếp hạng thành tích không gian 3D.
   - `F83`: Thư viện tài nguyên đồ họa 3D mở rộng.
   - `F85`: Dựng cảnh quan Torii và Núi Phú Sĩ 3D.
   - `F88`: Mô phỏng tương tác chuyển động môi trường 3D.
   - `F110`: Đồ họa hạt và ánh sáng WebGL chuyên sâu.
   *(Lý do: Tải nặng trình duyệt của học viên, dung lượng bundle tăng đột biến, không thiết yếu cho mục tiêu học ngữ pháp và từ vựng JLPT)*.
2. **Nhóm kiến trúc tách rời**:
   - `F75`: Tách riêng build tĩnh frontend độc lập (Static build tách rời Spring Boot).
   *(Lý do: Dự án duy trì kiến trúc Monolith tích hợp chặt chẽ qua Spring Boot + Thymeleaf + H2/MySQL để bảo đảm tính nhất quán của CSRF và CSP)*.
3. **Nhóm dịch vụ đám mây bên thứ ba**:
   - `F108`: Tích hợp Google Cloud Text-to-Speech API.
   *(Lý do: Loại bỏ hoàn toàn sự phụ thuộc vào mạng ngoài và chi phí API đám mây; toàn bộ tính năng phát âm sử dụng Web Speech API chuẩn trình duyệt bản địa, hoạt động offline ổn định)*.

---

## 4. DANH SÁCH CÔNG VIỆC CHƯA LÀM & KẾ HOẠCH BÀN GIAO TIẾP THEO

| Mã | Hạng mục công việc | Mô tả chi tiết & Yêu cầu chuẩn bị | Trạng thái hiện tại | Kế hoạch thực hiện |
| :-: | :--- | :--- | :-: | :--- |
| **F57** | Mô phỏng Hội thoại Nhập vai (Role-play Chat) | Xây dựng phòng luyện hội thoại phân vai tương tác giữa người học và NPC ảo. **Yêu cầu bắt buộc:** Phải tự tay viết kịch bản hội thoại hoàn chỉnh (chủ đề chào hỏi, đặt bàn nhà hàng, mua sắm, hỏi đường theo chuẩn Marugoto A1/A2), không dùng nội dung có bản quyền. | **CHƯA LÀM (Để sau)** | Soạn thảo kịch bản hội thoại trước trong `docs/samples/roleplay_dialogues.json`, sau đó mới xây dựng Controller và giao diện chat phân vai. |
| **F47** | Đọc chuỗi hội thoại theo hàng đợi (`queue`) | Cơ chế điều phối Web Speech API tự động phát âm nối tiếp phân vai hội thoại giữa người học và NPC, có khoảng dừng ngắt nghỉ tự nhiên. | **CHƯA LÀM** | Sẽ được tích hợp đồng thời cùng `F57` sau khi kịch bản hội thoại hoàn tất. |
| **F54** | Luyện gõ nhúng trong bài học (`TypingPractice`) | Script tự động bóc tách từ vựng DOM trong bài học thành khung gõ tương tác tại chỗ mà không cần chuyển trang. | **CHƯA LÀM** | Nghiên cứu giải pháp DOM parser nhẹ, tuân thủ nghiêm ngặt CSP. |
| **F70** | Thống kê điểm dịch câu toàn cục ngoài phòng thi | Bảng tổng kết chuyên sâu lịch sử điểm dịch thuật Levenshtein cho 40 câu dịch tự luận ngoài phòng thi. | **CHƯA LÀM** | Bổ sung màn hình báo cáo độc lập tại `/stats/translations`. |

---

## 5. ĐÁNH GIÁ SẴN SÀNG TRIỂN KHAI (PRODUCTION READINESS ASSESSMENT)

> [!WARNING]
> **Tuyên bố tính trung thực kỹ thuật:**  
> Toàn bộ mã nguồn hiện tại đã vượt qua **1,194/1,194 ca kiểm thử tự động**, đạt chuẩn an toàn thông tin theo kiểm toán tĩnh OWASP/CSP.  
> Tuy nhiên, **CHƯA ĐỦ BẰNG CHỨNG để kết luận hệ thống "hoàn toàn sẵn sàng sản xuất" (Production Ready)** cho đến khi các bước kiểm thử thực địa dưới đây được hoàn thành:

1. **Kiểm thử tải và chịu lỗi đồng thời (Load & Stress Testing):**
   - Cần thực hiện kiểm thử tải bằng JMeter hoặc k6 mô phỏng từ 200 – 500 người dùng đồng thời nộp bài thi `/exams/attempt/{id}/submit` và làm bài tại `/practice/studio`.
   - Cần đo lường ngưỡng tiêu thụ RAM của JVM và thời gian đáp ứng của pool kết nối HikariCP khi chịu tải cao.
2. **Kiểm thử động bên ngoài (Dynamic Application Security Testing - DAST):**
   - Cần quét thâm nhập thực tế bằng OWASP ZAP trên môi trường chạy thử (Staging) để phát hiện các lỗ hổng tiềm ẩn ở tầng network/reverse proxy.
3. **Cấu hình Reverse Proxy & Chứng chỉ số TLS:**
   - Cần thiết lập Nginx hoặc Caddy phía trước Spring Boot để đảm nhận việc SSL Termination (HTTPS thực tế với HTTP/2), cấu hình HSTS (`Strict-Transport-Security`) và Rate Limiting ở tầng mạng.
4. **Kiểm thử chấp nhận người dùng (User Acceptance Testing - UAT):**
   - Cần chạy thử nghiệm người dùng thật trên các trình duyệt khác nhau (Safari iOS, Chrome Android, Firefox, Edge) để kiểm tra độ trễ giọng nói Web Speech API và tương thích layout di động thực tế.
