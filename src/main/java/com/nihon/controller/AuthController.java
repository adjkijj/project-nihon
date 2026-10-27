package com.nihon.controller;

import com.nihon.config.RoleAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller tiện ích phục vụ khởi tạo phiên làm việc và vai trò người dùng (ADMIN, TEACHER, STUDENT)
 * hỗ trợ kiểm thử thủ công qua trình duyệt và mô phỏng xác thực người dùng.
 */
@Controller
public class AuthController {

    /**
     * Thiết lập phiên làm việc với vai trò chỉ định và chuyển hướng về trang import.
     *
     * @param role Vai trò người dùng (mặc định: ADMIN)
     * @param userId Định danh người dùng (mặc định: admin)
     * @param request Yêu cầu HTTP
     * @return Đường dẫn chuyển hướng
     */
    @GetMapping("/auth/login")
    public String login(@RequestParam(value = "role", defaultValue = "ADMIN") String role,
                        @RequestParam(value = "userId", defaultValue = "admin") String userId,
                        HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        session.setAttribute(RoleAuthInterceptor.SESSION_USER_ROLE, role);
        session.setAttribute(RoleAuthInterceptor.SESSION_USER_ID, userId);
        return "redirect:/import";
    }

    /**
     * Hủy phiên làm việc hiện tại (đăng xuất).
     *
     * @param request Yêu cầu HTTP
     * @return Chuyển hướng về trang chủ
     */
    @GetMapping("/auth/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }
}
