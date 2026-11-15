package com.nihon.config;

import com.nihon.service.excel.ExcelCellUtils;
import com.nihon.service.excel.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Interceptor kiểm soát truy cập phân hệ Quản trị (Admin Access Control).
 * Bảo vệ toàn bộ các endpoint quản trị và thao tác thay đổi học liệu gốc (tạo, sửa, xóa).
 *
 * Cơ chế bảo vệ:
 * 1. Cờ app.admin.enabled: Mặc định tắt (false) trên profile production. Khi false, trả về HTTP 404 Not Found.
 * 2. Giới hạn dải IP/CIDR được phép: app.admin.allowed-cidrs, phân giải qua ClientIpResolver.
 * 3. Giới hạn sai mật khẩu: tối đa 5 lần/phút/IP qua RateLimiterService (trả HTTP 429 Too Many Requests).
 * 4. Xác thực HTTP Basic: Đọc mật khẩu từ biến môi trường APP_ADMIN_PASSWORD, lưu hash BCrypt trong bộ nhớ,
 *    và so sánh hằng số thời gian bằng MessageDigest.isEqual.
 * 5. Ghi log kiểm toán mọi truy cập quản trị và tuyệt đối KHÔNG ghi mật khẩu.
 */
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessInterceptor.class);
    private static final int MAX_FAILED_ATTEMPTS_PER_MINUTE = 5;

    private final boolean adminEnabled;
    private final List<String> allowedCidrs;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;
    private final String adminBcryptHash;
    private final boolean isProdProfile;

    @Autowired
    public AdminAccessInterceptor(
            @Value("${app.admin.enabled:true}") boolean adminEnabled,
            @Value("${app.admin.allowed-cidrs:127.0.0.1,::1,0:0:0:0:0:0:0:1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7,fe80::/10}") String allowedCidrsConfig,
            @Autowired(required = false) ClientIpResolver clientIpResolver,
            @Autowired(required = false) RateLimiterService rateLimiterService,
            @Autowired(required = false) Environment environment) {
        this(adminEnabled, allowedCidrsConfig, clientIpResolver, rateLimiterService, resolveAdminPassword(environment), checkIsProd(environment));
    }

    public AdminAccessInterceptor(
            boolean adminEnabled,
            String allowedCidrsConfig,
            ClientIpResolver clientIpResolver,
            RateLimiterService rateLimiterService,
            String rawAdminPassword) {
        this(adminEnabled, allowedCidrsConfig, clientIpResolver, rateLimiterService, rawAdminPassword, false);
    }

    public AdminAccessInterceptor(
            boolean adminEnabled,
            String allowedCidrsConfig,
            ClientIpResolver clientIpResolver,
            RateLimiterService rateLimiterService,
            String rawAdminPassword,
            boolean isProdProfile) {
        this.adminEnabled = adminEnabled;
        this.clientIpResolver = (clientIpResolver != null) ? clientIpResolver : new ClientIpResolver();
        this.rateLimiterService = rateLimiterService;
        this.allowedCidrs = parseList(allowedCidrsConfig);
        this.isProdProfile = isProdProfile;

        // Lưu trữ duy nhất chuỗi hash BCrypt, không lưu bản rõ mật khẩu trong bộ nhớ cấu hình
        if (rawAdminPassword != null && !rawAdminPassword.isBlank()) {
            this.adminBcryptHash = BCrypt.hashpw(rawAdminPassword.trim(), BCrypt.gensalt(10));
        } else {
            this.adminBcryptHash = null;
        }
    }

    private static String resolveAdminPassword(Environment environment) {
        String envPassword = System.getenv("APP_ADMIN_PASSWORD");
        if (envPassword != null && !envPassword.isBlank()) {
            return envPassword.trim();
        }
        if (environment != null) {
            String propPassword = environment.getProperty("APP_ADMIN_PASSWORD");
            if (propPassword != null && !propPassword.isBlank()) {
                return propPassword.trim();
            }
        }
        return null;
    }

    private static boolean checkIsProd(Environment environment) {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String safeUri = ExcelCellUtils.sanitizeForLog(request.getRequestURI());

        // 1. Kiểm tra cờ bật/tắt chức năng quản trị (mặc định tắt trên prod -> HTTP 404)
        if (!adminEnabled) {
            log.warn("Truy cập trang quản trị [{}] bị từ chối do tính năng quản trị chưa được bật (app.admin.enabled=false)", safeUri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy trang yêu cầu.");
            return false;
        }

        // 2. Xác định IP và kiểm tra danh sách dải IP cho phép (allowed-cidrs)
        String clientIp = clientIpResolver.resolveClientIp(request);
        String safeIp = ExcelCellUtils.sanitizeForLog(clientIp);

        if (!isIpAllowed(clientIp)) {
            log.warn("Từ chối truy cập quản trị [{}]: Địa chỉ IP [{}] không nằm trong dải IP cho phép (app.admin.allowed-cidrs)", safeUri, safeIp);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Từ chối truy cập: Địa chỉ mạng của bạn không có quyền quản trị.");
            return false;
        }

        // 3. Kiểm tra biến môi trường mật khẩu quản trị
        if (this.adminBcryptHash == null) {
            if (isProdProfile) {
                log.error("Môi trường Production: APP_ADMIN_PASSWORD chưa được thiết lập! Từ chối truy cập quản trị [{}] từ IP [{}]", safeUri, safeIp);
                sendUnauthorized(response);
                return false;
            }
            // Trong môi trường dev/test khi chưa cấu hình mật khẩu quản trị, cho phép đi tiếp
            return true;
        }

        // 4. Kiểm tra giới hạn tần suất nhập sai mật khẩu (tối đa 5 lần/phút/IP)
        String rateLimitKey = "admin-fail:" + clientIp;
        if (rateLimiterService != null && rateLimiterService.isRateLimited(rateLimitKey, MAX_FAILED_ATTEMPTS_PER_MINUTE)) {
            log.warn("Từ chối truy cập quản trị [{}]: Địa chỉ IP [{}] đã vượt quá giới hạn 5 lần sai mật khẩu/phút", safeUri, safeIp);
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too Many Requests: Bạn đã nhập sai mật khẩu quá 5 lần trong vòng 1 phút. Vui lòng thử lại sau.");
            return false;
        }

        // 5. Kiểm tra tiêu đề xác thực HTTP Basic
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            sendUnauthorized(response);
            return false;
        }

        // 6. Phân tích thông tin xác thực Base64
        String base64Credentials = authHeader.substring(6).trim();
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64Credentials);
        } catch (IllegalArgumentException ex) {
            log.warn("Tiêu đề Authorization Basic không hợp lệ từ IP [{}] khi truy cập [{}]", safeIp, safeUri);
            recordPasswordFailure(rateLimitKey);
            sendUnauthorized(response);
            return false;
        }

        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
        int colonIndex = credentials.indexOf(':');
        String candidatePassword;
        if (colonIndex == -1) {
            candidatePassword = credentials;
        } else {
            candidatePassword = credentials.substring(colonIndex + 1);
        }

        // 7. So sánh mật khẩu bằng hash BCrypt và MessageDigest.isEqual (chống Timing Attack)
        String candidateHash = BCrypt.hashpw(candidatePassword, this.adminBcryptHash);
        boolean passwordValid = MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                this.adminBcryptHash.getBytes(StandardCharsets.UTF_8)
        );

        if (!passwordValid) {
            log.warn("Xác thực quản trị thất bại: Mật khẩu không chính xác từ IP [{}] khi truy cập [{}]", safeIp, safeUri);
            recordPasswordFailure(rateLimitKey);
            sendUnauthorized(response);
            return false;
        }

        // 8. Xác thực thành công: Ghi log kiểm toán (tuyệt đối không ghi mật khẩu)
        log.info("Xác thực quản trị thành công từ IP [{}] cho yêu cầu [{}] {}", safeIp, request.getMethod(), safeUri);
        if (rateLimiterService != null) {
            rateLimiterService.reset(rateLimitKey);
        }
        return true;
    }

    private void recordPasswordFailure(String rateLimitKey) {
        if (rateLimiterService != null) {
            rateLimiterService.recordFailure(rateLimitKey);
        }
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Nihon Admin\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    public boolean isIpAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (String pattern : allowedCidrs) {
            if (ClientIpResolver.matchesIpOrCidr(ip, pattern)) {
                return true;
            }
        }
        return false;
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
