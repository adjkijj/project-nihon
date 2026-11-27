package com.nihon.validation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích làm sạch và xác thực URL chuyển hướng an toàn (Safe Redirect Sanitizer).
 * Phòng chống tuyệt đối lỗ hổng chuyển hướng hở Open Redirect (SEC-004) và chèn giao thức mã độc.
 */
public final class SafeRedirect {

    private SafeRedirect() {
        // Utility class
    }

    /**
     * Làm sạch và kiểm tra URL chuyển hướng.
     * Quy tắc bắt buộc:
     * 1. Chỉ chấp nhận đường dẫn bắt đầu bằng một dấu gạch chéo '/' duy nhất.
     * 2. Không chứa '//' (ngăn chặn scheme-relative URL: //evil.com).
     * 3. Không chứa dấu ':' (ngăn chặn absolute URL: https:, http:, javascript:, data:).
     * 4. Không chứa dấu '\' (ngăn chặn backslash bypass: /\\evil.com).
     * 5. Không chứa ký tự điều khiển (ASCII 0-31, 127).
     * 6. Giải mã URL một lần bằng UTF-8 trước khi kiểm tra để chống kỹ thuật mã hóa URL (%2F, %5C, %3A).
     *
     * @param url URL đầu vào từ client request
     * @param fallback Đường dẫn an toàn dự phòng khi url không hợp lệ
     * @return URL an toàn hoặc fallback
     */
    public static String sanitize(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }

        String trimmed = url.trim();

        // 1. Giải mã URL một lần trước khi kiểm tra
        String decoded;
        try {
            decoded = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fallback;
        }

        // 2. Kiểm tra chuỗi sau khi giải mã (decoded)
        if (!isValidRelativePath(decoded)) {
            return fallback;
        }

        // 3. Kiểm tra chuỗi thô ban đầu (trimmed) để phòng chống bypass kép
        if (!isValidRelativePath(trimmed)) {
            return fallback;
        }

        return trimmed;
    }

    private static boolean isValidRelativePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Chỉ chấp nhận đường dẫn bắt đầu bằng đúng một dấu '/'
        if (!path.startsWith("/") || path.startsWith("//")) {
            return false;
        }

        // Không chứa '//' ở bất kỳ vị trí nào
        if (path.contains("//")) {
            return false;
        }

        // Không chứa dấu ':'
        if (path.contains(":")) {
            return false;
        }

        // Không chứa dấu '\'
        if (path.contains("\\")) {
            return false;
        }

        // Không chứa ký tự điều khiển
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (Character.isISOControl(c) || c <= 0x1F || c == 0x7F) {
                return false;
            }
        }

        return true;
    }
}

