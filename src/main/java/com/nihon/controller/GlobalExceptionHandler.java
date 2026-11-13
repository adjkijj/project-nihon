package com.nihon.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
     * Xử lý trường hợp dữ liệu bị trùng hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu (HTTP 409).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex, Model model) {
        log.error("Data integrity violation occurred", ex);
        model.addAttribute("errorTitle", "409 - Xung đột dữ liệu");
        model.addAttribute("errorMessage", "Dữ liệu bị trùng hoặc vi phạm ràng buộc");
        return "error";
    }

    /**
     * Xử lý trường hợp tham số kiểu dữ liệu không hợp lệ (ví dụ: ID là chuỗi thay vì số).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, RedirectAttributes redirectAttributes) {
        String safeName = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getName());
        String safeValue = com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(String.valueOf(ex.getValue()));
        log.warn("Method argument type mismatch for parameter [{}]: {}", safeName, safeValue);
        redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn hoặc tham số yêu cầu không hợp lệ.");
        return "redirect:/";
    }

    /**
     * Xử lý trường hợp không tìm thấy tài nguyên hoặc bản ghi trong cơ sở dữ liệu.
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException ex, Model model) {
        log.warn("Resource not found: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
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
        log.warn("Illegal argument encountered: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
        redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu yêu cầu không hợp lệ.");
        return "redirect:/";
    }

    /**
     * Xử lý trường hợp người dùng vượt quá tần suất tải lên cho phép (HTTP 429).
     */
    @ExceptionHandler(com.nihon.service.excel.RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String handleRateLimitExceeded(com.nihon.service.excel.RateLimitExceededException ex, Model model) {
        log.warn("Rate limit exceeded: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
        model.addAttribute("errorTitle", "429 - Quá giới hạn tần suất");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * Xử lý trường hợp tệp tin tải lên bị từ chối bởi bộ thẩm định an toàn (HTTP 400).
     */
    @ExceptionHandler(com.nihon.service.excel.UploadRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleUploadRejected(com.nihon.service.excel.UploadRejectedException ex, Model model) {
        log.warn("Upload rejected: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
        model.addAttribute("errorTitle", "400 - Tệp tin không hợp lệ");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * Xử lý trường hợp dung lượng tệp tin vượt quá cấu hình tối đa 5 MB của Spring (HTTP 400).
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex, Model model) {
        log.warn("Max upload size exceeded: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
        model.addAttribute("errorTitle", "400 - Dung lượng tệp quá lớn");
        model.addAttribute("errorMessage", "Dung lượng tệp tin tải lên vượt quá giới hạn tối đa 5 MB cho phép.");
        return "error";
    }

    /**
     * Xử lý trường hợp phương thức HTTP không được hỗ trợ trên đường dẫn (ví dụ: POST vào trang chỉ hỗ trợ GET -> HTTP 405).
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex, Model model) {
        log.warn("HTTP method not supported: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
        model.addAttribute("errorTitle", "405 - Phương thức không được hỗ trợ");
        model.addAttribute("errorMessage", "Phương thức truy cập không được phép trên đường dẫn này.");
        return "error";
    }

    /**
     * Xử lý ResponseStatusException (ví dụ 404 Not Found do controller/service chủ động ném).
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String handleResponseStatus(org.springframework.web.server.ResponseStatusException ex,
                                       jakarta.servlet.http.HttpServletResponse response,
                                       Model model) {
        response.setStatus(ex.getStatusCode().value());
        model.addAttribute("errorTitle", ex.getStatusCode().value() + " - " + (ex.getReason() != null ? ex.getReason() : "Lỗi"));
        model.addAttribute("errorMessage", ex.getReason() != null ? ex.getReason() : "Yêu cầu không thể thực hiện.");
        return "error";
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
