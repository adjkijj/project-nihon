package com.nihon.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Tiện ích phân giải địa chỉ IP của Client một cách an toàn.
 * Sử dụng trực tiếp request.getRemoteAddr() đã được xử lý bởi máy chủ web / Tomcat RemoteIpValve.
 * Không tự phân tích thủ công tiêu đề X-Forwarded-For để ngăn chặn kỹ thuật giả mạo IP (IP Spoofing).
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final List<String> trustedProxies;

    public ClientIpResolver() {
        this.trustedProxies = List.of();
    }

    public ClientIpResolver(@Value("${app.import.trusted-proxies:}") String trustedProxiesConfig) {
        this.trustedProxies = parseList(trustedProxiesConfig);
    }

    /**
     * Xác định địa chỉ IP thực tế của Client gửi yêu cầu.
     * Sử dụng trực tiếp request.getRemoteAddr() do servlet container / Tomcat phân giải an toàn.
     *
     * @param request Yêu cầu HTTP
     * @return Chuỗi địa chỉ IP đã xác minh hoặc "anonymous" nếu không xác định được
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "anonymous";
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr.trim();
        }

        return "anonymous";
    }

    /**
     * Kiểm tra địa chỉ IP có thuộc danh sách proxy tin cậy hay không.
     */
    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (String trusted : trustedProxies) {
            if (matchesIpOrCidr(ip, trusted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * So khớp địa chỉ IP với mẫu cấu hình (hỗ trợ localhost, so khớp chính xác và ký hiệu dải mạng CIDR).
     */
    public static boolean matchesIpOrCidr(String clientIp, String pattern) {
        if (clientIp == null || pattern == null) {
            return false;
        }
        String cleanIp = clientIp.trim();
        String cleanPattern = pattern.trim();

        if ("*".equals(cleanPattern)) {
            return true;
        }

        if ("localhost".equalsIgnoreCase(cleanPattern)) {
            return "127.0.0.1".equals(cleanIp)
                    || "::1".equals(cleanIp)
                    || "0:0:0:0:0:0:0:1".equals(cleanIp)
                    || "localhost".equalsIgnoreCase(cleanIp);
        }

        if (cleanPattern.contains("/")) {
            return matchesCidr(cleanIp, cleanPattern);
        }

        if (cleanPattern.equalsIgnoreCase(cleanIp)) {
            return true;
        }

        try {
            InetAddress ipAddr = InetAddress.getByName(cleanIp);
            InetAddress patternAddr = InetAddress.getByName(cleanPattern);
            return ipAddr.equals(patternAddr);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            InetAddress targetAddr = InetAddress.getByName(ip);
            InetAddress networkAddr = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);

            byte[] targetBytes = targetAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();

            if (targetBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            if (fullBytes > targetBytes.length) {
                return false;
            }

            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < targetBytes.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((targetBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
