package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.ImportUndoPreview;
import com.nihon.dto.excel.ImportUndoResult;
import com.nihon.entity.ImportAuditLog;
import com.nihon.service.excel.InMemoryMultipartFile;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimitExceededException;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Controller xử lý tải lên, xem trước và xác nhận nhập học liệu từ tệp tin Excel (.xlsx).
 * Không sử dụng tài khoản hay phân quyền vai trò (Role). An toàn dựa trên kiểm soát IP mạng,
 * bảo vệ chống CSRF, giới hạn tần suất 10 lần/IP/phút, và đảm bảo bước commit chỉ nhận tệp gốc
 * hoặc mã tạm 10 phút.
 */
@Controller
@RequestMapping("/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);
    private static final int MAX_DISPLAY_FILENAME_LENGTH = 100;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    private static final int MAX_HISTORY_PAGE = 100000;

    private final ImportService importService;
    private final ImportSessionStorage sessionStorage;
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;
    private final ResourceLoader resourceLoader;

    public ImportController(ImportService importService,
                            ImportSessionStorage sessionStorage,
                            RateLimiterService rateLimiterService,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) ClientIpResolver clientIpResolver,
                            ResourceLoader resourceLoader) {
        this.importService = importService;
        this.sessionStorage = sessionStorage;
        this.rateLimiterService = rateLimiterService;
        this.clientIpResolver = (clientIpResolver != null) ? clientIpResolver : new ClientIpResolver("127.0.0.1,::1,0:0:0:0:0:0:0:1");
        this.resourceLoader = resourceLoader;
    }

    /**
     * Hiển thị trang giao diện nhập học liệu Excel.
     */
    @GetMapping
    public String showImportPage(Model model) {
        populateDefaultModel(model);
        return "import";
    }

    /**
     * Hiển thị lịch sử các lần import đã thực hiện (phân trang 20 dòng/trang, mới nhất trước).
     * Chỉ cho phép xem, không có bất kỳ thao tác sửa hoặc xóa bản ghi kiểm toán nào.
     */
    @GetMapping("/history")
    public String showHistory(@RequestParam(value = "page", defaultValue = "0") int page,
                              Model model) {
        int safePage = Math.max(0, Math.min(MAX_HISTORY_PAGE, page));
        Pageable pageable = PageRequest.of(
                safePage,
                DEFAULT_HISTORY_PAGE_SIZE,
                Sort.by(Sort.Order.desc("importedAt"), Sort.Order.desc("id"))
        );
        Page<ImportAuditLog> historyPage = importService.findHistory(pageable);
        if (historyPage == null) {
            historyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        Map<Long, ImportUndoPreview> undoPreviews = importService.getUndoPreviews(historyPage.getContent());
        model.addAttribute("historyPage", historyPage);
        model.addAttribute("undoPreviews", undoPreviews != null ? undoPreviews : Collections.emptyMap());
        return "import-history";
    }

    /**
     * Hoàn tác (undo) một lần import thành công chưa hoàn tác.
     * Bảo vệ qua ImportSecurityInterceptor, CSRF và giới hạn tần suất theo địa chỉ IP.
     */
    @PostMapping("/history/{id}/undo")
    public String undoImport(@PathVariable("id") Long id,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }
        try {
            ImportUndoResult result = importService.undoImport(id, clientIp);
            if (result != null) {
                redirectAttributes.addFlashAttribute("successMessage", result.toNotificationMessage());
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã hoàn tác lần import #" + id + " thành công.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Hoàn tác lần import #{} bị từ chối: {}", id, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/import/history";
    }

    /**
     * Tải xuống tệp tin Excel mẫu có sẵn tiêu đề chuẩn (Update.xlsx).
     */
    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = resourceLoader.getResource("classpath:static/template/Update.xlsx");
        if (!resource.exists()) {
            File rootFile = new File("Update.xlsx");
            if (rootFile.exists()) {
                resource = new FileSystemResource(rootFile);
            }
        }

        if (!resource.exists()) {
            log.error("Không tìm thấy tệp tin mẫu Update.xlsx trên hệ thống");
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Update.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    /**
     * Xuất toàn bộ dữ liệu học liệu hiện tại (hai trang "New Word" và "Kanji") ra tệp tin Excel (.xlsx)
     * đúng cấu trúc chuẩn để có thể sao lưu, chỉnh sửa hàng loạt và nhập lại vào hệ thống.
     * Áp dụng giới hạn tần suất theo địa chỉ IP.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }
        try {
            byte[] excelBytes = importService.exportToExcel();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
            String filename = "nihon-export-" + timestamp + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (IllegalStateException ex) {
            log.warn("Xuất Excel bị từ chối do vượt giới hạn: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body(ex.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Tiếp nhận tệp tin Excel tải lên (multipart) để thẩm định và tạo báo cáo xem trước (preview).
     * Tạo mã tạm thời hạn 10 phút lưu trữ trong bộ nhớ tạm theo địa chỉ IP.
     */
    @PostMapping("/preview")
    public String preview(@RequestParam("file") MultipartFile file,
                          HttpServletRequest request,
                          Model model) {
        populateDefaultModel(model);

        // 1. Kiểm tra giới hạn tần suất theo địa chỉ IP (tối đa 10 lần/IP/phút -> HTTP 429)
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        // 2. Kiểm tra tính hợp lệ của tệp tin đầu vào
        if (file == null || file.isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng chọn tệp Excel (.xlsx) trước khi xem trước.");
            return "import";
        }

        try {
            // 3. Chạy thẩm định an toàn và phân tích preview
            ImportPreviewReport report = importService.preview(file);

            // 4. Lưu tệp gốc vào kho lưu trữ với mã tạm 10 phút gắn với clientIp
            String previewToken = sessionStorage.storeFile(clientIp, file);

            model.addAttribute("previewReport", report);
            model.addAttribute("previewToken", previewToken);
            model.addAttribute("fileName", sanitizeDisplayFilename(file.getOriginalFilename()));
            return "import";
        } catch (UploadRejectedException ex) {
            log.warn("Tệp tin tải lên bị từ chối: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
            model.addAttribute("errorMessage", ex.getMessage());
            return "import";
        } catch (Exception ex) {
            log.error("Lỗi không mong muốn trong quá trình xem trước tệp Excel", ex);
            model.addAttribute("errorMessage", "Không thể đọc tệp tin. Vui lòng kiểm tra định dạng và thử lại.");
            return "import";
        }
    }

    /**
     * Xác nhận nhập dữ liệu vào cơ sở dữ liệu (commit).
     * Chỉ tiếp nhận mã tạm thời hạn 10 phút hoặc tệp tin tải lại trực tiếp.
     */
    @PostMapping("/commit")
    public String commit(@RequestParam(value = "previewToken", required = false) String previewToken,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "mode", defaultValue = "SKIP_DUPLICATES") String rawMode,
                         HttpServletRequest request,
                         Model model) {
        populateDefaultModel(model);
        ImportMode mode = parseImportMode(rawMode);
        if (mode == null) {
            model.addAttribute("errorMessage", "Chế độ nhập dữ liệu không hợp lệ.");
            return "import";
        }
        model.addAttribute("selectedMode", mode);

        // 1. Kiểm tra giới hạn tần suất theo địa chỉ IP (tối đa 10 lần/IP/phút -> HTTP 429)
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        // 2. Xác định tệp tin nguồn: từ mã tạm 10 phút hoặc tải lại trực tiếp
        MultipartFile targetFile = null;
        if (previewToken != null && !previewToken.isBlank()) {
            String trimmedToken = previewToken.strip();
            if (!sessionStorage.isValidTokenFormat(trimmedToken)) {
                model.addAttribute("errorMessage", "Mã xem trước không hợp lệ");
                return "import";
            }
            Optional<ImportSessionStorage.StoredFile> storedOpt = sessionStorage.retrieveAndConsume(trimmedToken, clientIp);
            if (storedOpt.isEmpty()) {
                model.addAttribute("errorMessage", "Mã xem trước không hợp lệ hoặc đã hết hạn (quá 10 phút). Vui lòng tải lại tệp tin để xem trước.");
                return "import";
            }
            ImportSessionStorage.StoredFile stored = storedOpt.get();
            targetFile = new InMemoryMultipartFile(
                    "file",
                    stored.getOriginalFilename(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    stored.getContent()
            );
        } else if (file != null && !file.isEmpty()) {
            targetFile = file;
        } else {
            model.addAttribute("errorMessage", "Vui lòng chọn tệp tin hoặc hoàn tất xem trước trước khi xác nhận nhập.");
            return "import";
        }

        try {
            // 3. Thực thi transaction commit an toàn qua ImportService, ghi nhật ký kiểm toán theo clientIp
            ImportCommitResult result = importService.commit(targetFile, mode, clientIp);
            if (result.success()) {
                model.addAttribute("commitResult", result);
                model.addAttribute("successMessage", String.format(
                        "Nhập dữ liệu thành công! Đã thêm %d từ mới, cập nhật %d từ trùng; thêm %d Kanji mới, cập nhật %d Kanji trùng.",
                        result.wordsAdded(),
                        result.wordsUpdated(),
                        result.kanjiAdded(),
                        result.kanjiUpdated()
                ));
            } else {
                model.addAttribute("errorMessage", "Không thể ghi dữ liệu do tệp tin chứa lỗi. Toàn bộ thay đổi đã được hủy bỏ (rollback).");
                model.addAttribute("commitErrors", result.errors());
            }
            return "import";
        } catch (UploadRejectedException ex) {
            log.warn("Tệp tin commit bị từ chối: {}", com.nihon.service.excel.ExcelCellUtils.sanitizeForLog(ex.getMessage()));
            model.addAttribute("errorMessage", ex.getMessage());
            return "import";
        } catch (Exception ex) {
            log.error("Lỗi không mong muốn trong quá trình xác nhận nhập Excel", ex);
            model.addAttribute("errorMessage", "Không thể đọc tệp tin. Vui lòng kiểm tra định dạng và thử lại.");
            return "import";
        }
    }

    /**
     * Làm sạch tên tệp tin hiển thị: bỏ đường dẫn, loại ký tự điều khiển, cắt tối đa 100 ký tự.
     */
    public static String sanitizeDisplayFilename(String rawFilename) {
        if (rawFilename == null) {
            return "";
        }
        // Bỏ phần đường dẫn (cả '/' và '\')
        int lastSlash = Math.max(rawFilename.lastIndexOf('/'), rawFilename.lastIndexOf('\\'));
        String baseName = (lastSlash >= 0) ? rawFilename.substring(lastSlash + 1) : rawFilename;

        // Loại bỏ ký tự điều khiển Unicode (ISO Control / Cc)
        StringBuilder sb = new StringBuilder(baseName.length());
        baseName.codePoints().forEach(cp -> {
            if (!Character.isISOControl(cp) && Character.getType(cp) != Character.CONTROL) {
                sb.appendCodePoint(cp);
            }
        });

        String cleaned = sb.toString().strip();
        if (cleaned.length() > MAX_DISPLAY_FILENAME_LENGTH) {
            return cleaned.substring(0, MAX_DISPLAY_FILENAME_LENGTH);
        }
        return cleaned;
    }

    private ImportMode parseImportMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return ImportMode.SKIP_DUPLICATES;
        }
        try {
            return ImportMode.valueOf(rawMode.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Bổ sung các giá trị mặc định cho Model view.
     */
    private void populateDefaultModel(Model model) {
        if (!model.containsAttribute("modes")) {
            model.addAttribute("modes", ImportMode.values());
        }
        if (!model.containsAttribute("selectedMode")) {
            model.addAttribute("selectedMode", ImportMode.SKIP_DUPLICATES);
        }
    }
}
