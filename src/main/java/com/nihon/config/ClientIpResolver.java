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
 * Chỉ đọc tiêu đề X-Forwarded-For khi kết nối đến từ các Reverse Proxy đáng tin cậy (trusted-proxies).
 * Ngăn chặn tuyệt đối kỹ thuật giả mạo IP (IP Spoofing) qua header HTTP.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${app.import.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesConfig) {
        this.trustedProxies = parseList(trustedProxiesConfig);
    }

    /**
     * Xác định địa chỉ IP thực tế của Client gửi yêu cầu.
     *
     * @param request Yêu cầu HTTP
     * @return Chuỗi địa chỉ IP đã xác minh hoặc "anonymous" nếu không xác định được
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "anonymous";
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null) {
            remoteAddr = remoteAddr.trim();
        }

        // Chỉ đọc header X-Forwarded-For khi remoteAddr là proxy tin cậy được cấu hình
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] parts = xForwardedFor.split(",");
                if (parts.length > 0 && !parts[0].trim().isBlank()) {
                    String clientIp = parts[0].trim();
                    // Loại bỏ port nếu có (dạng 1.2.3.4:5678)
                    if (clientIp.contains(":") && !clientIp.contains("::") && clientIp.indexOf(':') == clientIp.lastIndexOf(':')) {
                        clientIp = clientIp.substring(0, clientIp.indexOf(':'));
                    }
                    return clientIp;
                }
            }
        }

        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr;
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
