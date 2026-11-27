# MA TRẬN PHÂN LOẠI ENDPOINT (ENDPOINT MATRIX)
## DỰ ÁN: PROJECT NIHON (HỆ THỐNG QUẢN LÝ VÀ HỌC TẬP TIẾNG NHẬT)

---

### Bối cảnh & Nguyên tắc Phân định Kiến trúc

Theo đặc tả kiến trúc của **PROJECT NIHON** và giải pháp khắc phục phát hiện **SEC-001**:
1. **Mô hình Cổng giáo dục mở (Open Educational Portal):** Ứng dụng không duy trì bảng tài khoản người dùng, không có đăng ký/đăng nhập cá nhân hóa cho học viên. Học viên được định danh ẩn danh thông qua Cookie `nihon_learner` (UUIDv4) để lưu trữ tiến độ học tập và bài thi.
2. **Không áp đặt phân quyền người học:** Các trang đọc học liệu công khai (`/vocabulary`, `/kanji`, `/grammar`, `/topics`, `/verbs`) và các endpoint người học nộp bài, luyện tập được giữ mở hoàn toàn cho người học mà không yêu cầu mật khẩu hay tài khoản.
3. **Bảo vệ tập trung phân hệ Quản trị (`AdminAccessInterceptor`):** Toàn bộ các trang quản trị và thao tác thay đổi học liệu gốc (tạo, sửa, xóa từ vựng, Kanji, ngữ pháp, đề thi, import/undo) được bảo vệ tập trung thông qua `AdminAccessInterceptor` với:
   - Cờ bật/tắt: `app.admin.enabled` (mặc định tắt trên Production -> HTTP 404).
   - Xác thực HTTP Basic với mật khẩu quản trị từ biến môi trường `APP_ADMIN_PASSWORD` (lưu hash BCrypt, so sánh hằng số thời gian bằng `MessageDigest.isEqual`).
   - Kiểm soát mạng theo dải IP/CIDR: `app.admin.allowed-cidrs`.
   - Chống tấn công dò mật khẩu: `RateLimiterService` (tối đa 5 lần sai mật khẩu/phút/IP).

---

## 1. BẢNG PHÂN LOẠI CHI TIẾT (ENDPOINT MATRIX)

### 1.1. Nhóm 1: Quản trị dùng (Admin Endpoints) - Bảo vệ bởi `AdminAccessInterceptor`

Toàn bộ các endpoint dưới đây yêu cầu xác thực `AdminAccessInterceptor`:

| STT | Phương thức HTTP | Đường dẫn (Path Pattern) | Lớp Controller | Mô tả chức năng | Loại thao tác |
| :---: | :---: | :--- | :--- | :--- | :---: |
| 1 | `GET` | `/exams/admin` | `ExamAdminController` | Danh sách bài thi quản trị | Xem quản trị |
| 2 | `GET` | `/exams/admin/new` | `ExamAdminController` | Màn hình tạo đề thi mới | Form tạo |
| 3 | `POST` | `/exams/admin/new` | `ExamAdminController` | Tiếp nhận tạo đề thi mới | Tạo (Ghi) |
| 4 | `GET` | `/exams/admin/{id}` | `ExamAdminController` | Chi tiết cấu trúc đề thi | Xem quản trị |
| 5 | `GET` | `/exams/admin/{id}/edit` | `ExamAdminController` | Màn hình sửa thông tin đề thi | Form sửa |
| 6 | `POST` | `/exams/admin/{id}/edit` | `ExamAdminController` | Cập nhật thông tin đề thi | Sửa (Ghi) |
| 7 | `POST` | `/exams/admin/{id}/toggle-publish` | `ExamAdminController` | Bật/tắt trạng thái phát hành đề thi | Sửa (Ghi) |
| 8 | `POST` | `/exams/admin/{id}/delete` | `ExamAdminController` | Xóa bài thi | Xóa (Ghi) |
| 9 | `POST` | `/exams/admin/{id}/sections` | `ExamAdminController` | Thêm phần thi vào bài thi | Tạo (Ghi) |
| 10 | `POST` | `/exams/admin/{id}/sections/{sectionId}/edit` | `ExamAdminController` | Cập nhật tiêu đề phần thi | Sửa (Ghi) |
| 11 | `POST` | `/exams/admin/{id}/sections/{sectionId}/delete` | `ExamAdminController` | Xóa phần thi | Xóa (Ghi) |
| 12 | `POST` | `/exams/admin/{id}/sections/{sectionId}/move-up` | `ExamAdminController` | Đổi thứ tự phần thi lên | Sửa (Ghi) |
| 13 | `POST` | `/exams/admin/{id}/sections/{sectionId}/move-down` | `ExamAdminController` | Đổi thứ tự phần thi xuống | Sửa (Ghi) |
| 14 | `GET` | `/exams/admin/{id}/questions/new` | `ExamAdminController` | Màn hình tạo câu hỏi bài thi | Form tạo |
| 15 | `POST` | `/exams/admin/{id}/questions/new` | `ExamAdminController` | Thêm câu hỏi vào đề thi | Tạo (Ghi) |
| 16 | `GET` | `/exams/admin/{id}/questions/{qId}/edit` | `ExamAdminController` | Màn hình sửa câu hỏi đề thi | Form sửa |
| 17 | `POST` | `/exams/admin/{id}/questions/{qId}/edit` | `ExamAdminController` | Cập nhật câu hỏi đề thi | Sửa (Ghi) |
| 18 | `POST` | `/exams/admin/{id}/questions/{qId}/delete` | `ExamAdminController` | Xóa câu hỏi khỏi đề thi | Xóa (Ghi) |
| 19 | `POST` | `/exams/admin/{id}/questions/{qId}/move-up` | `ExamAdminController` | Đổi thứ tự câu hỏi lên | Sửa (Ghi) |
| 20 | `POST` | `/exams/admin/{id}/questions/{qId}/move-down` | `ExamAdminController` | Đổi thứ tự câu hỏi xuống | Sửa (Ghi) |
| 21 | `GET` | `/exams/admin/import` | `ExamAdminController` | Màn hình import đề thi từ Excel | Form import |
| 22 | `POST` | `/exams/admin/import/preview` | `ExamAdminController` | Xem trước file import đề thi | Ghi tạm |
| 23 | `POST` | `/exams/admin/import/commit` | `ExamAdminController` | Thực thi import đề thi vào CSDL | Tạo hàng loạt |
| 24 | `GET` | `/exams/admin/template` | `ExamAdminController` | Tải file Excel mẫu đề thi | Xuất tệp |
| 25 | `GET` | `/exams/admin/{id}/export` | `ExamAdminController` | Xuất đề thi ra file Excel | Xuất tệp |
| 26 | `GET` | `/grammar/admin` | `GrammarController` | Điều hướng trang quản trị ngữ pháp | Chuyển hướng |
| 27 | `GET` | `/grammar/admin/topics` | `GrammarController` | Danh sách chủ đề ngữ pháp quản trị | Xem quản trị |
| 28 | `GET` | `/grammar/admin/topics/new` | `GrammarController` | Màn hình tạo cấu trúc ngữ pháp | Form tạo |
| 29 | `POST` | `/grammar/admin/topics/new` | `GrammarController` | Tạo cấu trúc ngữ pháp mới | Tạo (Ghi) |
| 30 | `GET` | `/grammar/admin/{id}` | `GrammarController` | Chi tiết cấu trúc ngữ pháp quản trị | Xem quản trị |
| 31 | `GET` | `/grammar/admin/{id}/edit` | `GrammarController` | Màn hình sửa cấu trúc ngữ pháp | Form sửa |
| 32 | `POST` | `/grammar/admin/{id}/edit` | `GrammarController` | Cập nhật cấu trúc ngữ pháp | Sửa (Ghi) |
| 33 | `POST` | `/grammar/admin/{id}/delete` | `GrammarController` | Xóa cấu trúc ngữ pháp | Xóa (Ghi) |
| 34 | `GET` | `/grammar/admin/{topicId}/questions/new` | `GrammarController` | Màn hình tạo câu hỏi trắc nghiệm | Form tạo |
| 35 | `POST` | `/grammar/admin/{topicId}/questions/new` | `GrammarController` | Thêm câu hỏi trắc nghiệm ngữ pháp | Tạo (Ghi) |
| 36 | `GET` | `/grammar/admin/{topicId}/questions/{qId}/edit` | `GrammarController` | Màn hình sửa câu hỏi trắc nghiệm | Form sửa |
| 37 | `POST` | `/grammar/admin/{topicId}/questions/{qId}/edit` | `GrammarController` | Cập nhật câu hỏi trắc nghiệm | Sửa (Ghi) |
| 38 | `POST` | `/grammar/admin/{topicId}/questions/{qId}/delete` | `GrammarController` | Xóa câu hỏi trắc nghiệm ngữ pháp | Xóa (Ghi) |
| 39 | `POST` | `/grammar/admin/{topicId}/questions/{qId}/move-up` | `GrammarController` | Đổi thứ tự câu hỏi ngữ pháp lên | Sửa (Ghi) |
| 40 | `POST` | `/grammar/admin/{topicId}/questions/{qId}/move-down` | `GrammarController` | Đổi thứ tự câu hỏi ngữ pháp xuống | Sửa (Ghi) |
| 41 | `GET` | `/grammar/admin/import` | `GrammarController` | Màn hình import ngữ pháp từ Excel | Form import |
| 42 | `POST` | `/grammar/admin/import/preview` | `GrammarController` | Xem trước file import ngữ pháp | Ghi tạm |
| 43 | `POST` | `/grammar/admin/import/commit` | `GrammarController` | Lưu dữ liệu ngữ pháp vào CSDL | Tạo hàng loạt |
| 44 | `GET` | `/grammar/admin/export` | `GrammarController` | Xuất dữ liệu ngữ pháp ra Excel | Xuất tệp |
| 45 | `GET` | `/grammar/admin/template` | `GrammarController` | Tải file Excel mẫu ngữ pháp | Xuất tệp |
| 46 | `GET` | `/import` | `ImportController` | Màn hình chính nhập học liệu Excel | Xem quản trị |
| 47 | `POST` | `/import/preview` | `ImportController` | Phân tích và xem trước tệp Excel | Ghi tạm |
| 48 | `POST` | `/import/commit` | `ImportController` | Ghi dữ liệu từ vựng/Kanji vào CSDL | Tạo hàng loạt |
| 49 | `GET` | `/import/history` | `ImportController` | Xem nhật ký kiểm toán các lần import | Xem quản trị |
| 50 | `POST` | `/import/history/{id}/undo` | `ImportController` | Hoàn tác (Undo) lần nhập dữ liệu | Xóa/Hồi phục (Ghi) |
| 51 | `GET` | `/import/template` | `ImportController` | Tải tệp tin Excel mẫu chuẩn | Xuất tệp |
| 52 | `GET` | `/import/export` | `ImportController` | Sao lưu toàn bộ từ vựng ra Excel | Xuất tệp |
| 53 | `GET` | `/vocabulary/new` | `VocabularyController` | Màn hình thêm từ vựng mới | Form tạo |
| 54 | `POST` | `/vocabulary/new` | `VocabularyController` | Lưu từ vựng mới vào kho dữ liệu | Tạo (Ghi) |
| 55 | `GET` | `/vocabulary/{id}/edit` | `VocabularyController` | Màn hình chỉnh sửa từ vựng | Form sửa |
| 56 | `POST` | `/vocabulary/{id}/edit` | `VocabularyController` | Cập nhật thông tin từ vựng | Sửa (Ghi) |
| 57 | `POST` | `/vocabulary/{id}/delete` | `VocabularyController` | Xóa từ vựng khỏi CSDL | Xóa (Ghi) |
| 58 | `POST` | `/vocabulary/bulk-delete` | `VocabularyController` | Xóa hàng loạt từ vựng đã chọn | Xóa hàng loạt |
| 59 | `POST` | `/vocabulary/move-topic` | `VocabularyController` | Chuyển chủ đề cho nhiều từ vựng | Sửa hàng loạt |
| 60 | `GET` | `/kanji/new` | `KanjiController` | Màn hình thêm Kanji mới | Form tạo |
| 61 | `POST` | `/kanji/new` | `KanjiController` | Lưu chữ Kanji mới vào CSDL | Tạo (Ghi) |
| 62 | `GET` | `/kanji/{id}/edit` | `KanjiController` | Màn hình sửa Kanji | Form sửa |
| 63 | `POST` | `/kanji/{id}/edit` | `KanjiController` | Cập nhật thông tin Kanji | Sửa (Ghi) |
| 64 | `POST` | `/kanji/{id}/delete` | `KanjiController` | Xóa chữ Kanji khỏi CSDL | Xóa (Ghi) |
| 65 | `GET` | `/topics/new` | `TopicController` | Màn hình thêm chủ đề mới | Form tạo |
| 66 | `POST` | `/topics/new` | `TopicController` | Tạo chủ đề học tập mới | Tạo (Ghi) |
| 67 | `GET` | `/topics/{id}/edit` | `TopicController` | Màn hình sửa tên chủ đề | Form sửa |
| 68 | `POST` | `/topics/{id}/edit` | `TopicController` | Cập nhật thông tin chủ đề | Sửa (Ghi) |
| 69 | `POST` | `/topics/{id}/delete` | `TopicController` | Xóa chủ đề (khi không còn học liệu) | Xóa (Ghi) |
| 70 | `POST` | `/topics/{id}/move` | `TopicController` | Đổi thứ tự sắp xếp chủ đề | Sửa (Ghi) |
| 71 | `POST` | `/topics/{id}/move-content` | `TopicController` | Di dời toàn bộ học liệu sang chủ đề khác | Sửa hàng loạt |
| 72 | `GET` | `/verbs/new` | `VerbController` | Màn hình thêm động từ mới | Form tạo |
| 73 | `POST` | `/verbs/new` | `VerbController` | Lưu động từ mới vào CSDL | Tạo (Ghi) |
| 74 | `GET` | `/verbs/{id}/edit` | `VerbController` | Màn hình sửa động từ | Form sửa |
| 75 | `POST` | `/verbs/{id}/edit` | `VerbController` | Cập nhật thông tin động từ | Sửa (Ghi) |
| 76 | `POST` | `/verbs/{id}/delete` | `VerbController` | Xóa động từ khỏi CSDL | Xóa (Ghi) |

---

### 1.2. Nhóm 2: Người học dùng (Learner Endpoints) - KHÔNG CẦN XÁC THỰC QUẢN TRỊ

Toàn bộ các endpoint dưới đây phục vụ học viên tra cứu, luyện tập, làm bài thi ẩn danh; **tuyệt đối không áp đặt xác thực quản trị**:

| STT | Phương thức HTTP | Đường dẫn (Path Pattern) | Lớp Controller | Mô tả chức năng học tập | Cơ chế bảo vệ hiện tại |
| :---: | :---: | :--- | :--- | :--- | :--- |
| 1 | `GET` | `/` | `HomeController` | Trang chủ hệ thống | Công khai (Public) |
| 2 | `GET` | `/vocabulary` | `VocabularyController` | Tra cứu danh sách từ vựng, lọc | Công khai, có Rate limit export |
| 3 | `GET` | `/vocabulary/export-text` | `VocabularyController` | Xuất text cho Anki/Quizlet | Rate limited (20 lượt/phút) |
| 4 | `GET` | `/flashcards` | `FlashcardController` | Học từ vựng qua thẻ Flashcard | Công khai |
| 5 | `GET` | `/flashcards/study` | `FlashcardController` | Chế độ lật thẻ học tập | Công khai |
| 6 | `GET` | `/kanji` | `KanjiController` | Bảng tra cứu chữ Kanji | Công khai |
| 7 | `GET` | `/kanji/{id}` | `KanjiController` | Chi tiết chữ Kanji & thứ tự nét | Công khai |
| 8 | `GET` | `/kanji/export-text` | `KanjiController` | Xuất danh sách Kanji dạng text | Rate limited |
| 9 | `GET` | `/grammar` | `GrammarController` | Bảng mục lục ngữ pháp | Công khai |
| 10 | `GET` | `/grammar/lookup` | `GrammarController` | Tra cứu nhanh mẫu ngữ pháp | Công khai |
| 11 | `GET` | `/grammar/table` | `GrammarController` | Bảng tổng hợp ngữ pháp | Công khai |
| 12 | `GET` | `/grammar/cards` | `GrammarController` | Thẻ học ngữ pháp | Công khai |
| 13 | `GET` | `/grammar/furigana-guide` | `GrammarController` | Hướng dẫn bật/tắt Furigana | Công khai |
| 14 | `POST` | `/grammar/{id}/toggle-progress`| `GrammarController` | Đánh dấu/Hủy 'Đã học' mẫu ngữ pháp | CSRF + `nihon_learner` cookie + `SafeRedirect` |
| 15 | `POST` | `/grammar/{id}/quiz/check` | `GrammarController` | Chấm bài trắc nghiệm mini tại thẻ | CSRF + `nihon_learner` cookie |
| 16 | `GET` | `/grammar/arena` | `GrammarController` | Đấu trường ngữ pháp (chọn chủ đề) | Công khai |
| 17 | `POST` | `/grammar/arena/start` | `GrammarController` | Khởi tạo phiên đấu trường | CSRF + Session |
| 18 | `GET` | `/grammar/arena/run` | `GrammarController` | Giao diện trả lời câu hỏi đấu trường | Session |
| 19 | `POST` | `/grammar/arena/answer` | `GrammarController` | Nộp câu trả lời đấu trường | CSRF + Session |
| 20 | `GET` | `/grammar/arena/result` | `GrammarController` | Bảng kết quả đấu trường | Session |
| 21 | `GET` | `/topics` | `TopicController` | Danh mục chủ đề học tập | Công khai |
| 22 | `GET` | `/topics/{id}` | `TopicController` | Chi tiết học liệu trong chủ đề | Công khai |
| 23 | `GET` | `/topics/{id}/hub` | `TopicHubController` | Trang Hub tổng hợp của chủ đề | Công khai |
| 24 | `GET` | `/topics/{id}/test` | `TopicController` | Đề kiểm tra tổng hợp chủ đề | Công khai |
| 25 | `POST` | `/topics/{id}/test/submit` | `TopicController` | Nộp bài kiểm tra tổng hợp chủ đề | CSRF + `nihon_learner` cookie |
| 26 | `GET` | `/verbs` | `VerbController` | Danh mục động từ tiếng Nhật | Công khai |
| 27 | `GET` | `/verbs/{id}` | `VerbController` | Bảng chia 7 thể của động từ | Công khai |
| 28 | `GET` | `/verbs/export-text` | `VerbController` | Xuất danh sách động từ dạng text | Rate limited |
| 29 | `GET` | `/verbs/lab` | `VerbController` | Phòng thí nghiệm chia thể động từ | Công khai |
| 30 | `GET` | `/verbs/practice` | `VerbPracticeController` | Màn hình thiết lập luyện động từ | Công khai |
| 31 | `GET` | `/verbs/practice/question` | `VerbPracticeController` | Câu hỏi luyện chia thể | Session |
| 32 | `POST` | `/verbs/practice/answer` | `VerbPracticeController` | Nộp câu trả lời luyện chia thể | CSRF + Session |
| 33 | `GET` | `/verbs/practice/result` | `VerbPracticeController` | Kết quả luyện chia thể động từ | Session |
| 34 | `GET` | `/exams` | `ExamTakingController` | Danh sách đề thi JLPT tự luyện | Công khai |
| 35 | `GET` | `/exams/{code}` | `ExamTakingController` | Chi tiết đề thi & nút bắt đầu làm | Công khai |
| 36 | `POST` | `/exams/{code}/start` | `ExamTakingController` | Bắt đầu phiên làm bài thi | CSRF + `nihon_learner` + Session |
| 37 | `GET` | `/exams/attempt/{id}` | `ExamTakingController` | Màn hình làm bài thi trực tuyến | Giấu đáp án trong HTML + Session |
| 38 | `POST` | `/exams/attempt/{id}/answer` | `ExamTakingController` | Lưu câu trả lời từng câu | CSRF + Session + `nihon_learner` |
| 39 | `POST` | `/exams/attempt/{id}/finish` | `ExamTakingController` | Nộp bài và chấm điểm tự động | CSRF + Session + `nihon_learner` |
| 40 | `GET` | `/exams/attempt/{id}/result` | `ExamTakingController` | Bảng điểm chi tiết và giải thích | Cách ly theo `nihon_learner` |
| 41 | `POST` | `/exams/attempt/{id}/retry` | `ExamTakingController` | Làm lại toàn bộ đề thi | CSRF + `nihon_learner` |
| 42 | `POST` | `/exams/attempt/{id}/retry-wrong` | `ExamTakingController` | Luyện tập lại chỉ các câu sai | CSRF + `nihon_learner` |
| 43 | `POST` | `/exams/attempt/{id}/review/{qId}` | `ExamTakingController` | Tự chấm điểm câu dịch viết | CSRF + `nihon_learner` |
| 44 | `GET` | `/history` | `HistoryController` | Lịch sử học tập & điểm thi cá nhân | Cách ly theo `nihon_learner` cookie |
| 45 | `POST` | `/history/delete-all` | `HistoryController` | Xóa sạch lịch sử học tập cá nhân | CSRF + `nihon_learner` cookie |
| 46 | `GET` | `/practice` | `PracticeController` | Trung tâm luyện tập gõ từ vựng | Công khai |
| 47 | `GET` | `/practice/choice` | `PracticeChoiceController` | Luyện tập trắc nghiệm từ vựng | Công khai |
| 48 | `GET` | `/practice/vi-ja` | `PracticeViJaController` | Luyện tập dịch Việt - Nhật | Công khai |
| 49 | `GET` | `/practice/kanji` | `KanjiPracticeController` | Luyện tập nhận diện Hán tự Kanji | Công khai |
| 50 | `GET` | `/practice/timed` | `TimedPracticeController` | Luyện tập tính giờ thử thách | Công khai |
| 51 | `GET` | `/practice/studio` | `PracticeStudioController` | Không gian luyện gõ tập trung | Công khai |
| 52 | `GET` | `/stats` | `StatsController` | Thống kê số lượng học liệu | Công khai |

---

### 1.3. Tổng kết Phân bổ

- **Tổng số endpoint Quản trị (Admin):** 76 routes -> Phải đăng ký bảo vệ qua `AdminAccessInterceptor`.
- **Tổng số endpoint Người học (Learner):** 52 routes -> Hoạt động tự do không mật khẩu, bảo vệ toàn vẹn bằng CSRF, Cookie UUIDv4 và kiểm tra sở hữu dữ liệu.

