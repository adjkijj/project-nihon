package com.nihon.config;

import com.nihon.service.excel.ExcelCellUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * Interceptor kiểm soát an ninh mạng cho tính năng Import học liệu (/import/**).
 * Do hệ thống không sử dụng tài khoản hay phân quyền người dùng (Role), việc bảo vệ tính năng import
 * dựa trên kiểm soát tầng mạng (Network Access Control):
 * 1. Cấu hình bật/tắt toàn hệ thống: app.import.enabled (mặc định: true). Khi tắt, trả về HTTP 404 Not Found.
 * 2. Giới hạn dải IP truy cập: app.import.allowed-ips (mặc định: localhost và mạng nội bộ). Khi IP ngoài danh sách, trả HTTP 403 Forbidden.
 */
@Component
public class ImportSecurityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ImportSecurityInterceptor.class);

    private final boolean importEnabled;
    private final List<String> allowedIps;
    private final ClientIpResolver clientIpResolver;

    public ImportSecurityInterceptor(
            @Value("${app.import.enabled:true}") boolean importEnabled,
            @Value("${app.import.allowed-ips:127.0.0.1,::1,0:0:0:0:0:0:0:1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7,fe80::/10}") String allowedIpsConfig,
            @Autowired(required = false) ClientIpResolver clientIpResolver,
            @Value("${app.import.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesConfig) {
        this.importEnabled = importEnabled;
        this.clientIpResolver = (clientIpResolver != null) ? clientIpResolver : new ClientIpResolver(trustedProxiesConfig);
        this.allowedIps = parseList(allowedIpsConfig);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Kiểm tra trạng thái bật/tắt của tính năng import
        if (!importEnabled) {
            String safeUri = ExcelCellUtils.sanitizeForLog(request.getRequestURI());
            log.warn("Truy cập vào [{}] bị từ chối do tính năng import đã bị vô hiệu hóa (app.import.enabled=false)", safeUri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy trang yêu cầu.");
            return false;
        }

        // 2. Kiểm tra địa chỉ IP của Client so với danh sách allowed-ips
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!isIpAllowed(clientIp)) {
            String safeUri = ExcelCellUtils.sanitizeForLog(request.getRequestURI());
            String safeIp = ExcelCellUtils.sanitizeForLog(clientIp);
            log.warn("Từ chối truy cập [{}]: Địa chỉ IP [{}] không nằm trong danh sách được phép (allowed-ips)", safeUri, safeIp);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Từ chối truy cập: Địa chỉ mạng của bạn không có quyền sử dụng tính năng này.");
            return false;
        }

        return true;
    }

    public boolean isIpAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        for (String pattern : allowedIps) {
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
