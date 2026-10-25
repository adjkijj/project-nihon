# Danh Sách Việc Cần Làm Trước Khi Phát Hành (Pre-Release Checklist)
### Dự án: Project Nihon - Ứng Dụng Học Từ Vựng & Luyện Phản Xạ Tiếng Nhật

Tài liệu này tổng hợp toàn bộ các hạng mục công việc cần kiểm tra, cấu hình và hoàn thiện trước khi chính thức đưa ứng dụng lên môi trường Production (Go-Live). Danh sách được chia theo 4 trụ cột chính:
1. **Bảo mật và Kỹ thuật nền tảng**
2. **Trải nghiệm người dùng (UX/UI) và Đa thiết bị**
3. **Nội dung và Pháp lý**
4. **Tối ưu hóa công cụ tìm kiếm (SEO)**

---

## 1. Bảo Mật và Kỹ Thuật Nền Tảng (Security & Infrastructure)

### 1.1. Cấu hình TLS / HTTPS & Mạng
- [ ] **Chấm dứt TLS (TLS Termination) tại Reverse Proxy (Nginx / Cloudflare / Traefik / ALB)**:
  - Chỉ cho phép các giao thức bảo mật cao: `TLSv1.2` và `TLSv1.3`.
  - Nghiêm cấm hoàn toàn các giao thức cũ có lỗ hổng bảo mật: `SSLv2`, `SSLv3`, `TLSv1.0`, `TLSv1.1`.
  - Cấu hình cipher suites mạnh (AEAD ciphers: `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384`, `ECDHE-RSA-AES128-GCM-SHA256`).
- [ ] **Chứng chỉ SSL hợp lệ**:
  - Cài đặt chứng chỉ SSL từ đơn vị cấp chứng chỉ uy tín (Let's Encrypt / Certbot hoặc Commercial CA).
  - Thiết lập cron job / systemd timer tự động gia hạn chứng chỉ trước ngày hết hạn tối thiểu 30 ngày.
  - *Lưu ý*: Tuyệt đối không dùng chứng chỉ tự ký (Self-signed) trên Production.
- [ ] **Ủy quyền Header và IP Proxy an toàn**:
  - Kiểm tra Spring Boot kích hoạt `server.forward-headers-strategy=framework`.
  - Chỉ định rõ dải IP của Reverse Proxy (`server.tomcat.remoteip.internal-proxies`), ngăn chặn kẻ xấu giả mạo header `X-Forwarded-Proto` hoặc `X-Forwarded-For`.
- [ ] **HSTS (HTTP Strict Transport Security)**:
  - Kích hoạt header `Strict-Transport-Security: max-age=31536000; includeSubDomains` khi kết nối an toàn (`request.isSecure() == true`).
  - Đảm bảo HTTP tự động chuyển hướng (301 Permanent Redirect) sang HTTPS.

### 1.2. Quản lý Secret, Khóa và Biến Môi Trường
- [ ] **Không commit Secret vào Git**:
  - Đã đưa vào `.gitignore`: `*.p12`, `*.jks`, `*.keystore`, `*.crt`, `*.key`, `*.pem`, `.env`, `secrets/`.
  - Tuyệt đối không hardcode mật khẩu database, keystore password, JWT secret vào mã nguồn hay file `.properties`.
- [ ] **Nạp Secret qua Biến Môi Trường hoặc Secret Manager**:
  - Môi trường chạy Spring Boot phải nhận thông tin nhạy cảm qua biến:
    - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
    - `SSL_ENABLED`, `SSL_KEY_STORE_PATH`, `SSL_KEY_STORE_PASSWORD` (nếu chạy Standalone)
  - Sử dụng công cụ quản lý bí mật (AWS Secrets Manager, HashiCorp Vault hoặc file `.env` được phân quyền chặt chẽ `chmod 600`).

### 1.3. Cơ sở dữ liệu Production (MySQL)
- [ ] **Mã hóa kết nối Database**:
  - Chuỗi kết nối JDBC bắt buộc mã hóa: `useSSL=true&requireSSL=true`.
- [ ] **Phân quyền người dùng Database (Principle of Least Privilege)**:
  - Không sử dụng user `root` để kết nối từ Spring Boot.
  - Tạo user chuyên dụng với các quyền tối thiểu cần thiết: `SELECT`, `INSERT`, `UPDATE`, `DELETE` trên schema `nihon_db`.
  - Thu hồi quyền `DROP`, `ALTER`, `GRANT`, `SUPER`.
- [ ] **Tắt H2 Console**:
  - Đảm bảo `spring.h2.console.enabled=false` trên profile `prod`.
- [ ] **Kiểm tra Migration / DDL**:
  - Đặt `spring.jpa.hibernate.ddl-auto=validate` để ngăn Hibernate tự động thay đổi cấu trúc bảng trên Production.
  - Sử dụng Flyway hoặc Liquibase để quản lý lịch sử nâng cấp schema nếu dự án phát triển lâu dài.

### 1.4. Bảo vệ Ứng dụng & OWASP Compliance
- [ ] **Bảo vệ CSRF**:
  - Tất cả các thao tác thay đổi dữ liệu (`POST`, `PUT`, `DELETE`, `PATCH`) bắt buộc phải có CSRF token hợp lệ được lưu trong Session.
  - Interceptor so sánh token theo thời gian hằng số (`MessageDigest.isEqual`) để chống Timing Attack.
- [ ] **Security Headers toàn diện**:
  - `X-Frame-Options: DENY` (Chống Clickjacking).
  - `X-Content-Type-Options: nosniff` (Chống MIME-type sniffing).
  - `Referrer-Policy: strict-origin-when-cross-origin` (Bảo vệ thông tin URL nội bộ).
  - Cân nhắc bổ sung `Content-Security-Policy (CSP)` phù hợp với tài nguyên CSS/JS cục bộ.
- [ ] **Chống Injection & XSS**:
  - 100% câu truy vấn dùng Spring Data JPA / Hibernate tham số hóa (Parameterized Queries).
  - Thymeleaf tự động escape dữ liệu người dùng (`th:text`), không dùng thẻ `th:utext` cho input chưa kiểm duyệt.
  - Dữ liệu đầu vào từ Form/DTO được validate chặt chẽ về độ dài, định dạng, ký tự đặc biệt.
- [ ] **Che giấu chi tiết lỗi & Stack Trace**:
  - `server.error.include-stacktrace=never`, `server.error.include-message=never`, `server.error.whitelabel.enabled=false`.
  - Bộ xử lý tập trung `GlobalExceptionHandler` bắt lỗi và chuyển hướng kèm thông báo an toàn, không để lộ cấu trúc code/DB.

### 1.5. Giám sát, Vận hành và Dự phòng thảm họa (Disaster Recovery)
- [ ] **Quản lý Log (Logback / SLF4J)**:
  - Cấu hình Log Rotation (giới hạn dung lượng mỗi file tối đa 50MB, lưu tối đa 30 ngày) để tránh làm đầy ổ cứng máy chủ.
  - Không log mật khẩu, session token hoặc dữ liệu cá nhân.
- [ ] **Giám sát sức khỏe ứng dụng (Health Checks & Metrics)**:
  - Tích hợp Spring Boot Actuator (`/actuator/health`) chỉ mở cho nội bộ hoặc giám sát viên được phân quyền.
- [ ] **Sao lưu dữ liệu tự động (Backup Strategy)**:
  - Thiết lập cron job tự động `mysqldump` hàng ngày, nén file và lưu trữ an toàn tại vùng nhớ độc lập (off-site / S3).
  - Đã diễn tập quy trình khôi phục dữ liệu (Restore drill) thành công từ bản sao lưu.
- [ ] **Phạm vi PCI-DSS**:
  - Ghi rõ và xác nhận: Ứng dụng **không thu thập, xử lý hay lưu trữ thẻ thanh toán**, do đó **nằm ngoài phạm vi (Out of Scope) của PCI-DSS**. Không thêm tính năng thanh toán giả định.

---

## 2. Trải Nghiệm Người Dùng (UX/UI) và Đa Thiết Bị

### 2.1. Tương thích Đa thiết bị (Responsive Design)
- [ ] **Kiểm thử trên thiết bị di động (Mobile)**:
  - Kiểm tra trên các màn hình phổ biến (iPhone SE 375px, iPhone 14/15 393px, Samsung Galaxy 360-412px).
  - Thanh menu điều hướng (Navigation bar) cuộn ngang mượt mà, không bị vỡ giao diện.
  - Không có thanh cuộn ngang ngoài ý muốn (`overflow-x: hidden`).
- [ ] **Kiểm thử trên Máy tính bảng (Tablet) & Màn hình lớn (Desktop)**:
  - Bố cục căn giữa (Centered layout), giới hạn chiều rộng tối đa (`max-width: 720px` - `1200px`) giúp mắt không phải di chuyển quá rộng.

### 2.2. Trải nghiệm Bàn phím & Phản xạ (Keyboard-centric Learning)
- [ ] **Luồng gõ phím mượt mà không cần chuột**:
  - Ô nhập liệu đầu tiên tự động nhận con trỏ (`autofocus`).
  - Hỗ trợ phím **Enter** để nộp bài kiểm tra.
  - Ở chế độ kiểm tra Kanji có yêu cầu cả cách đọc Hiragana và nghĩa: Phím Enter khi gõ xong Hiragana tự động chuyển focus xuống ô nhập Nghĩa tiếng Việt.
- [ ] **Chống gian lận & Trải nghiệm thực chất**:
  - Tuyệt đối không gửi đáp án chuẩn xuống HTML hoặc mã JavaScript của trình duyệt trước khi người học nộp bài.
  - Chấm điểm hoàn toàn tại máy chủ; hỗ trợ chuẩn hóa khoảng trắng và Unicode (NFC/NFKC).

### 2.3. Khả năng tiếp cận & Hiển thị chữ tiếng Nhật (Accessibility & Typography)
- [ ] **Kích thước và Font chữ tiếng Nhật**:
  - Chữ Hán (Kanji) và chữ Kana phải có kích thước đủ lớn (`font-size >= 2rem` trong bài kiểm tra, nét chữ rõ ràng).
  - Thiết lập font fallback chuẩn cho tiếng Nhật trên cả Windows, macOS, iOS và Android:
    `font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Hiragino Sans", "Meiryo", "Yu Gothic", sans-serif;`.
- [ ] **Độ tương phản màu sắc (WCAG 2.1 AA)**:
  - Độ tương phản giữa chữ và nền đạt tỷ lệ tối thiểu 4.5:1.
  - Màu thông báo kết quả Đúng (Xanh lá) và Sai (Đỏ) luôn đi kèm nhãn văn bản biểu đạt rõ ràng, không chỉ dựa vào màu sắc (dành cho người khiếm sắc).
- [ ] **Trạng thái rỗng & Thông báo (Empty States & Feedback)**:
  - Khi chủ đề chưa có từ vựng hoặc tìm kiếm không thấy kết quả: Hiển thị hướng dẫn trực quan, có nút quay lại hoặc thêm mới.
  - Thông báo thành công / thất bại hiển thị rõ ràng, biến mất hợp lý hoặc có nút đóng.

---

## 3. Nội Dung và Pháp Lý (Content & Legal/Compliance)

### 3.1. Tính Chính Xác Của Dữ Liệu Học Tập
- [ ] **Rà soát dữ liệu từ vựng & Kanji**:
  - Kiểm tra đối chiếu danh sách từ vựng, âm Hán Việt, Onyomi, Kunyomi và nghĩa tiếng Việt với các nguồn chuẩn mực (từ điển Kanjidic, EDICT, sách giáo trình chuẩn).
  - Kiểm tra các từ đồng âm khác nghĩa, các trường âm (`お` vs `う`) và âm ngắt (`っ`).
- [ ] **Xử lý dữ liệu chưa hoàn chỉnh**:
  - Đối với các từ Kanji thiếu cách đọc chuẩn trong cơ sở dữ liệu gốc: Đảm bảo hệ thống hiển thị thông báo rõ ràng, không tự ý suy đoán bừa bãi và bỏ qua yêu cầu kiểm tra Hiragana để tránh trừ điểm sai của học viên.
- [ ] **Kiểm tra tính Idempotent của tiến trình nhập liệu**:
  - Đảm bảo file import `tu-vung-tieng-nhat-da-sap-xep.md` có thể chạy lại an toàn mà không sinh ra dữ liệu trùng lặp.

### 3.2. Chính Sách Quyền Riêng Tư & Điều Khoản Sử Dụng (Privacy & Terms)
- [ ] **Chính sách quyền riêng tư (Privacy Policy)**:
  - Tuyên bố minh bạch dữ liệu lưu trữ: Ứng dụng hiện lưu tiến độ học tập tạm thời theo `HttpSession` (Cookie phiên), không lưu mật khẩu hay dữ liệu cá nhân nhạy cảm.
  - Nếu không thu thập email/tên cá nhân: Nêu rõ ứng dụng hoạt động ẩn danh, không bán hay chia sẻ dữ liệu cho bên thứ ba.
- [ ] **Điều khoản sử dụng & Miễn trừ trách nhiệm (Terms & Disclaimer)**:
  - Nêu rõ Project Nihon là công cụ học tập hỗ trợ ghi nhớ từ vựng và chữ Hán.
  - Tuyên bố miễn trừ trách nhiệm: Không cam kết kết quả đậu kỳ thi JLPT chính thức và không thay thế cho các tổ chức khảo thí giáo dục Nhật Bản.
- [ ] **Bản quyền & Ghi nhận nguồn (Attribution)**:
  - Ghi nhận đầy đủ bản quyền các thư viện nguồn mở, font chữ và tài liệu tham khảo được sử dụng trong dự án.

---

## 4. Tối Ưu Hóa Công Cụ Tìm Kiếm (SEO)

### 4.1. Cấu Trúc HTML Ngữ Nghĩa & Thẻ Meta
- [ ] **Thẻ Title và Meta Description độc nhất cho từng trang**:
  - Trang chủ: Tiêu đề rõ ràng, mô tả súc tích mục đích học từ vựng tiếng Nhật.
  - Trang Từ vựng: Title chứa từ khóa danh mục từ vựng, cách đọc, tra cứu.
  - Trang Kanji: Title làm nổi bật tính năng tra cứu chữ Hán, bộ thủ, Onyomi, Kunyomi.
  - Trang Luyện tập: Mô tả chế độ kiểm tra phản xạ bàn phím hai chiều (Nhật-Việt, Việt-Nhật).
- [ ] **Thẻ Heading phân cấp chuẩn**:
  - Mỗi trang chỉ có duy nhất **một** thẻ `<h1>`.
  - Các tiểu mục sử dụng đúng cấp bậc `<h2>`, `<h3>` tương ứng; không nhảy cóc thứ bậc thẻ heading.
- [ ] **Cấu trúc ngữ nghĩa (Semantic Markup)**:
  - Sử dụng đúng các thẻ `<header>`, `<nav>`, `<main>`, `<section>`, `<footer>`.

### 4.2. Khả Năng Chia Sẻ Mạng Xã Hội (Open Graph & Twitter Cards)
- [ ] **Bổ sung thẻ Open Graph (OG)**:
  ```html
  <meta property="og:title" content="Project Nihon - Luyện Phản Xạ Từ Vựng & Kanji Tiếng Nhật" />
  <meta property="og:description" content="Ứng dụng học từ vựng, tra cứu chữ Hán Kanji và luyện phản xạ gõ phím trực tiếp hai chiều Nhật - Việt." />
  <meta property="og:type" content="website" />
  <meta property="og:url" content="https://nihon.yourdomain.com/" />
  <meta property="og:image" content="https://nihon.yourdomain.com/images/og-thumbnail.png" />
  ```
- [ ] **Bổ sung thẻ Twitter Card**:
  ```html
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Project Nihon - Học Từ Vựng Tiếng Nhật" />
  <meta name="twitter:description" content="Nền tảng kiểm tra phản xạ ghi nhớ từ vựng tiếng Nhật không dùng trắc nghiệm." />
  <meta name="twitter:image" content="https://nihon.yourdomain.com/images/og-thumbnail.png" />
  ```

### 4.3. Chỉ Mục Công Cụ Tìm Kiếm (Crawling & Indexing)
- [ ] **Tạo file `robots.txt`**:
  - Cho phép bot thu thập thông tin các trang nội dung công khai (`/`, `/vocabulary`, `/kanji`, `/topics`).
  - Chặn bot quét các đường dẫn thay đổi trạng thái, làm bài kiểm tra hoặc gây tốn tài nguyên máy chủ:
    ```txt
    User-agent: *
    Allow: /
    Allow: /vocabulary
    Allow: /kanji
    Allow: /topics
    Disallow: /practice/check
    Disallow: /practice/vi-ja/check
    Disallow: /vocabulary/new
    Disallow: /vocabulary/*/edit
    Disallow: /topics/new
    Disallow: /stats
    Sitemap: https://nihon.yourdomain.com/sitemap.xml
    ```
- [ ] **Tạo file `sitemap.xml`**:
  - Khai báo danh sách các URL trang chủ, danh sách từ vựng, danh sách Kanji để Google và các máy tìm kiếm dễ dàng lập chỉ mục.
- [ ] **Thẻ Canonical URL**:
  - Đảm bảo mỗi trang có thẻ `<link rel="canonical" href="..." />` trỏ về đúng URL chuẩn HTTPS, tránh trùng lặp nội dung giữa phiên bản `http://`, `https://`, `www` và không `www`.

### 4.4. Hiệu Năng Tải Trang & Core Web Vitals
- [ ] **Tối ưu tốc độ tải trang (PageSpeed)**:
  - Tận dụng CSS và JavaScript thuần (Vanilla) có sẵn trong dự án; không thêm các thư viện cồng kềnh ngoài ý muốn.
  - Đảm bảo điểm số Core Web Vitals (LCP, FID/INP, CLS) đạt ngưỡng Tốt (Màu xanh).
- [ ] **Favicon chuẩn**:
  - Đặt favicon đầy đủ các kích thước (16x16, 32x32, `apple-touch-icon.png`) trong thư mục `src/main/resources/static/favicon.ico`.

---

## 5. Tóm Tắt Trình Tự Triển Khai (Deployment Runbook)

| Bước | Hành động | Ghi chú |
| :---: | :--- | :--- |
| **1** | Chạy kiểm thử: `.\mvnw.cmd clean test` | Đảm bảo 114/114 bài test đều PASS |
| **2** | Đóng gói ứng dụng: `.\mvnw.cmd package` | Tạo file `target/com-0.0.1-SNAPSHOT.jar` |
| **3** | Cấu hình máy chủ Linux & Nginx | Thiết lập SSL Let's Encrypt, TLS 1.2/1.3, Proxy Pass |
| **4** | Cấu hình MySQL Production | Tạo user riêng, kích hoạt mã hóa SSL, chạy script schema |
| **5** | Khởi chạy ứng dụng với Profile `prod` | Truyền biến môi trường `DB_*`, tắt debug |
| **6** | Kiểm tra khói (Smoke Test) thực tế | Kiểm tra HTTPS trên trình duyệt, thử làm bài test, xem bảng Kanji |
| **7** | Kích hoạt giám sát & sao lưu tự động | Bật cron job backup DB và log rotation |

---
*Tài liệu được khởi tạo phục vụ phát hành an toàn cho Project Nihon. Cần rà soát và đánh dấu hoàn thành từng mục trước khi nghiệm thu.*
