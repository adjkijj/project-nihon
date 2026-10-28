# Hướng Dẫn Thiết Lập HTTPS & Bảo Mật Kết Nối Cho Môi Trường Phát Triển (DEV)

Tài liệu này hướng dẫn cách khởi tạo chứng chỉ SSL/TLS tự ký (Self-Signed Certificate) dành riêng cho môi trường phát triển máy trạm, thiết lập các biến môi trường và thực hiện kiểm tra an toàn theo yêu cầu của hệ thống.

---

## 1. Khởi tạo Chứng chỉ SSL/TLS tự ký (PKCS12 Keystore)

Sử dụng công cụ chuẩn `keytool` (có sẵn trong JDK 21) để tạo tệp keystore định dạng PKCS12:

```bash
keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore-dev.p12 \
  -validity 365 \
  -storepass devpassword123 \
  -keypass devpassword123 \
  -dname "CN=localhost, OU=Development, O=ProjectNihon, L=Hanoi, C=VN" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

> **LƯU Ý BẢO MẬT:**
> - Tệp `keystore-dev.p12` đã được đưa vào `.gitignore` (`*.p12`, `*.keystore`).
> - **TUYỆT ĐỐI KHÔNG** commit tệp keystore hoặc mật khẩu lên kho mã nguồn Git.

---

## 2. Thiết lập Biến Môi Trường (Environment Variables)

Hệ thống đọc đường dẫn keystore và mật khẩu hoàn toàn từ biến môi trường:

### Trên Windows PowerShell:
```powershell
$env:SSL_ENABLED="true"
$env:SSL_KEY_STORE_PATH="keystore-dev.p12"
$env:SSL_KEY_STORE_PASSWORD="devpassword123"
$env:SSL_KEY_STORE_TYPE="PKCS12"
$env:SSL_KEY_ALIAS="localhost"
$env:HTTPS_REDIRECT_ENABLED="true"
```

### Trên Linux / macOS Bash:
```bash
export SSL_ENABLED="true"
export SSL_KEY_STORE_PATH="keystore-dev.p12"
export SSL_KEY_STORE_PASSWORD="devpassword123"
export SSL_KEY_STORE_TYPE="PKCS12"
export SSL_KEY_ALIAS="localhost"
export HTTPS_REDIRECT_ENABLED="true"
```

---

## 3. Khởi chạy Ứng dụng với Profile Production (hoặc Dev bật SSL)

```powershell
# Chạy với profile prod
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## 4. Các Lệnh Kiểm Tra Thủ Công & Kết Quả Dự Kiến

### A. Kiểm tra giao thức TLS bằng `openssl s_client`

1. **Kiểm tra TLS 1.3 (Thành công):**
   ```bash
   openssl s_client -connect localhost:8443 -tls1_3
   ```
   *Kết quả mong đợi:* Kết nối thành công (`SSL-Session: Protocol  : TLSv1.3`, Cipher thuộc bộ AEAD).

2. **Kiểm tra TLS 1.2 (Thành công):**
   ```bash
   openssl s_client -connect localhost:8443 -tls1_2
   ```
   *Kết quả mong đợi:* Kết nối thành công (`SSL-Session: Protocol  : TLSv1.2`, Cipher thuộc ECDHE-GCM).

3. **Kiểm tra các giao thức cũ bị cấm (TLS 1.0, TLS 1.1, SSLv3):**
   ```bash
   openssl s_client -connect localhost:8443 -tls1_1
   openssl s_client -connect localhost:8443 -tls1
   ```
   *Kết quả mong đợi:* Bị máy chủ từ chối kết nối ngay lập tức (`handshake failure` hoặc `Connection refused`).

### B. Kiểm tra HTTP Security Headers bằng `curl -I`

1. **Kiểm tra trang chủ qua HTTPS:**
   ```bash
   curl -k -I https://localhost:8443/
   ```
   *Kết quả phản hồi:*
   ```http
   HTTP/1.1 200 OK
   Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
   X-Content-Type-Options: nosniff
   Referrer-Policy: strict-origin-when-cross-origin
   Permissions-Policy: camera=(), microphone=(), geolocation=()
   Strict-Transport-Security: max-age=31536000; includeSubDomains
   X-Frame-Options: DENY
   ```

2. **Kiểm tra Cache-Control cho trang upload (`/import`):**
   ```bash
   curl -k -I https://localhost:8443/import
   ```
   *Kết quả phản hồi:*
   ```http
   Cache-Control: no-store, no-cache, must-revalidate, max-age=0
   Pragma: no-cache
   ```

3. **Kiểm tra chuyển hướng toàn bộ HTTP sang HTTPS (301 Moved Permanently):**
   ```bash
   curl -I http://localhost:8080/
   ```
   *Kết quả phản hồi:*
   ```http
   HTTP/1.1 301 Moved Permanently
   Location: https://localhost:8443/
   ```

4. **Kiểm tra ứng dụng chạy sau Reverse Proxy (nhận diện `X-Forwarded-Proto`):**
   ```bash
   curl -I -H "X-Forwarded-Proto: http" http://localhost:8080/
   ```
   *Kết quả phản hồi:* Nhận HTTP 301 chuyển hướng sang `https://...`.

---

## 5. Danh mục các cấu hình quan trọng đã thiết lập

1. **Giao thức & Ciphers**: Chỉ TLS 1.2 và TLS 1.3; chỉ dùng AEAD (GCM, ChaCha20) và ECDHE.
2. **Ẩn phiên bản**: `server.server-header=` và tùy biến Tomcat xóa bỏ header Server.
3. **Tắt Directory Listing**: Cấu hình `listings=false` trên Tomcat DefaultServlet.
4. **Tắt Whitelabel Error Stack Trace**: `server.error.include-stacktrace=never`, `server.error.whitelabel.enabled=false`.
5. **Reverse Proxy Support**: Nhận diện `X-Forwarded-Proto` qua `server.forward-headers-strategy=framework`.
