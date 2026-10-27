package com.nihon.service.excel;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kho lưu trữ tệp tin tạm thời gắn với mã xem trước (preview token) trong phiên người dùng.
 * Mã tạm có thời hạn hiệu lực tối đa 10 phút. Bước commit chỉ nhận tệp tin tải lại hoặc mã tạm hợp lệ,
 * hoàn toàn không tin cậy hay nhận dữ liệu đã parse gửi lên từ client.
 */
@Component
public class ImportSessionStorage {

    public static final long TOKEN_VALIDITY_MINUTES = 10L;

    /**
     * Bản ghi lưu trữ tệp tin tải lên trong bộ nhớ tạm thời.
     */
    public static class StoredFile {
        private final String token;
        private final String userKey;
        private final String originalFilename;
        private final byte[] content;
        private final Instant expiresAt;

        public StoredFile(String token, String userKey, String originalFilename, byte[] content, Instant expiresAt) {
            this.token = token;
            this.userKey = userKey;
            this.originalFilename = originalFilename;
            this.content = (content != null) ? content.clone() : new byte[0];
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public String getUserKey() {
            return userKey;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public byte[] getContent() {
            return content.clone();
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, StoredFile> store = new ConcurrentHashMap<>();

    /**
     * Lưu trữ tệp tin tải lên và tạo mã tạm có thời hạn 10 phút.
     *
     * @param userKey Định danh người dùng hoặc phiên làm việc
     * @param file Tệp tin MultipartFile tải lên từ người dùng
     * @return Mã chuỗi ngẫu nhiên (UUID) gắn với tệp tin
     * @throws IOException Nếu không thể đọc mảng byte từ tệp tin
     */
    public String storeFile(String userKey, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tin tải lên không được rỗng");
        }
        cleanupExpired();

        String token = UUID.randomUUID().toString();
        byte[] bytes = file.getBytes();
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);

        store.put(token, new StoredFile(token, userKey, file.getOriginalFilename(), bytes, expiresAt));
        return token;
    }

    /**
     * Lấy tệp tin theo mã tạm và xóa khỏi kho lưu trữ (chỉ dùng một lần).
     *
     * @param token Mã tạm
     * @param userKey Định danh người dùng yêu cầu commit
     * @return Optional chứa StoredFile nếu mã hợp lệ, đúng người dùng và chưa hết hạn 10 phút
     */
    public Optional<StoredFile> retrieveAndConsume(String token, String userKey) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        cleanupExpired();

        StoredFile stored = store.get(token.trim());
        if (stored == null) {
            return Optional.empty();
        }

        if (stored.isExpired()) {
            store.remove(token.trim());
            return Optional.empty();
        }

        // Kiểm tra khớp người dùng sở hữu mã tạm
        if (stored.getUserKey() != null && userKey != null && !stored.getUserKey().equals(userKey.trim())) {
            return Optional.empty();
        }

        // Xóa để đảm bảo token chỉ được sử dụng một lần
        store.remove(token.trim());
        return Optional.of(stored);
    }

    /**
     * Dọn dẹp các mã tạm đã quá hạn 10 phút.
     */
    public void cleanupExpired() {
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Xóa toàn bộ kho lưu trữ (hỗ trợ kiểm thử).
     */
    public void clear() {
        store.clear();
    }
}
