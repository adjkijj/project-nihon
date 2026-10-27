package com.nihon.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Interceptor kiểm tra phân quyền truy cập tại máy chủ (Server-side Role Authorization).
 * Chỉ người dùng sở hữu vai trò Quản trị viên (ADMIN) hoặc Giáo viên (TEACHER) mới được phép
 * truy cập vào các chức năng nhập dữ liệu Excel (/import/**).
 * Mọi yêu cầu chưa xác thực (chưa đăng nhập) hoặc không đủ vai trò sẽ nhận phản hồi HTTP 403 Forbidden.
 */
@Component
public class RoleAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RoleAuthInterceptor.class);

    public static final String SESSION_USER_ROLE = "USER_ROLE";
    public static final String SESSION_USER_ID = "USER_ID";

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN",
            "TEACHER",
            "ROLE_ADMIN",
            "ROLE_TEACHER"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            String safeUri = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(request.getRequestURI());
            log.warn("Từ chối truy cập [{}]: Yêu cầu chưa đăng nhập phiên làm việc (HTTP 403)", safeUri);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Yêu cầu đăng nhập với vai trò quản trị viên hoặc giáo viên.");
            return false;
        }

        Object roleObj = session.getAttribute(SESSION_USER_ROLE);
        if (roleObj == null) {
            roleObj = session.getAttribute("ROLE");
        }

        String role = roleObj != null ? roleObj.toString().trim().toUpperCase() : null;
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            String safeUri = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(request.getRequestURI());
            String safeRole = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(role);
            log.warn("Từ chối truy cập [{}]: Vai trò [{}] không có quyền sử dụng tính năng import (HTTP 403)", safeUri, safeRole);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện chức năng nhập dữ liệu (Cần quyền ADMIN hoặc TEACHER).");
            return false;
        }

        return true;
    }
}
