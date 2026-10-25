package com.nihon.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

/**
 * Bộ xử lý ngoại lệ tập trung toàn cục (OWASP A05:2021 Security Misconfiguration).
 * Ngăn chặn tuyệt đối việc rò rỉ stack trace, cấu trúc cơ sở dữ liệu hay thông tin framework ra ngoài trình duyệt.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Xử lý trường hợp tham số kiểu dữ liệu không hợp lệ (ví dụ: ID là chuỗi thay vì số).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, RedirectAttributes redirectAttributes) {
        log.warn("Method argument type mismatch for parameter [{}]: {}", ex.getName(), ex.getValue());
        redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn hoặc tham số yêu cầu không hợp lệ.");
        return "redirect:/";
    }

    /**
     * Xử lý trường hợp không tìm thấy tài nguyên hoặc bản ghi trong cơ sở dữ liệu.
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorTitle", "404 - Không tìm thấy nội dung");
        model.addAttribute("errorMessage", "Nội dung bạn đang tìm kiếm không tồn tại hoặc đã bị xóa khỏi hệ thống.");
        return "error";
    }

    /**
     * Xử lý trường hợp đường dẫn tài nguyên tĩnh không tồn tại.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResourceFound(NoResourceFoundException ex, Model model) {
        model.addAttribute("errorTitle", "404 - Trang không tồn tại");
        model.addAttribute("errorMessage", "Đường dẫn bạn yêu cầu không tồn tại trên hệ thống.");
        return "error";
    }

    /**
     * Xử lý các lỗi tham số nghiệp vụ không hợp lệ.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, RedirectAttributes redirectAttributes) {
        log.warn("Illegal argument encountered: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu yêu cầu không hợp lệ.");
        return "redirect:/";
    }

    /**
     * Bắt tất cả các lỗi không mong muốn khác: Ghi log chi tiết trên server nhưng chỉ trả về thông báo chung cho client.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unhandled internal server error occurred", ex);
        model.addAttribute("errorTitle", "500 - Lỗi máy chủ nội bộ");
        model.addAttribute("errorMessage", "Hệ thống gặp sự cố trong quá trình xử lý yêu cầu. Vui lòng thử lại sau.");
        return "error";
    }
}
