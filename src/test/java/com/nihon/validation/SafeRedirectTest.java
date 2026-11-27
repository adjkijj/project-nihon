package com.nihon.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tính năng bảo vệ chuyển hướng an toàn của SafeRedirect (SEC-004):
 * Thẩm định các trường hợp tấn công Open Redirect, Scheme Relative, Backslash Bypass, Double Encoding, JavaScript URLs.
 */
class SafeRedirectTest {

    private static final String FALLBACK = "/grammar";

    // =========================================================================
    // 1. CÁC TRƯỜNG HỢP KIỂM THỬ THEO YÊU CẦU CỦA ĐẶC TẢ
    // =========================================================================

    @Test
    @DisplayName("1. Absolute URL với protocol 'https://evil.com' -> trả về fallback")
    void sanitize_absoluteHttpsUrl_returnsFallback() {
        String result = SafeRedirect.sanitize("https://evil.com", FALLBACK);
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("2. Scheme-relative URL '//evil.com' -> trả về fallback")
    void sanitize_schemeRelativeUrl_returnsFallback() {
        String result = SafeRedirect.sanitize("//evil.com", FALLBACK);
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("3. Backslash bypass '/\\evil.com' -> trả về fallback")
    void sanitize_backslashBypass_returnsFallback() {
        String result = SafeRedirect.sanitize("/\\evil.com", FALLBACK);
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("4. URL encoded bypass '/%2F%2Fevil.com' -> trả về fallback sau khi decode")
    void sanitize_urlEncodedDoubleSlash_returnsFallback() {
        String result = SafeRedirect.sanitize("/%2F%2Fevil.com", FALLBACK);
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("5. JavaScript pseudo-protocol 'javascript:alert(1)' -> trả về fallback")
    void sanitize_javascriptUrl_returnsFallback() {
        String result = SafeRedirect.sanitize("javascript:alert(1)", FALLBACK);
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("6. Đường dẫn tương đối hợp lệ '/grammar/1' -> giữ nguyên đường dẫn")
    void sanitize_validRelativePath_returnsPath() {
        String result = SafeRedirect.sanitize("/grammar/1", FALLBACK);
        assertThat(result).isEqualTo("/grammar/1");
    }

    // =========================================================================
    // 2. CÁC TRƯỜNG HỢP BIÊN VÀ BIẾN THỂ TẤN CÔNG BẢO MẬT NÂNG CAO
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.com",
            "ftp://files.evil.com",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox",
            "//sub.evil.com/path",
            "///triple-slash.com",
            "/\\/\\evil.com",
            "/%5Cevil.com",
            "/%3Aevil.com",
            "\\evil.com",
            "evil.com/grammar",
            "about:blank",
            "jav&#x09;ascript:alert(1)"
    })
    @DisplayName("7. Các dạng URL độc hại khác đều bị từ chối và trả về fallback")
    void sanitize_maliciousUrls_returnFallback(String maliciousUrl) {
        assertThat(SafeRedirect.sanitize(maliciousUrl, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("8. Đầu vào null, rỗng hoặc chỉ chứa khoảng trắng -> trả về fallback")
    void sanitize_nullOrBlank_returnsFallback() {
        assertThat(SafeRedirect.sanitize(null, FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SafeRedirect.sanitize("", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SafeRedirect.sanitize("   ", FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("9. Chứa ký tự điều khiển (CRLF Injection \\r\\n) -> trả về fallback")
    void sanitize_controlChars_returnsFallback() {
        assertThat(SafeRedirect.sanitize("/grammar\r\nSet-Cookie: evil=1", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SafeRedirect.sanitize("/grammar\nSet-Cookie: evil=1", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SafeRedirect.sanitize("/grammar\tpath", FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("10. Đường dẫn nội bộ kèm query string và anchor hợp lệ -> chấp nhận an toàn")
    void sanitize_validRelativePathsWithQuery_returnsPath() {
        assertThat(SafeRedirect.sanitize("/grammar?page=2&level=N3", FALLBACK))
                .isEqualTo("/grammar?page=2&level=N3");
        assertThat(SafeRedirect.sanitize("/grammar/topics/5", FALLBACK))
                .isEqualTo("/grammar/topics/5");
        assertThat(SafeRedirect.sanitize("/topics", FALLBACK))
                .isEqualTo("/topics");
    }
}

