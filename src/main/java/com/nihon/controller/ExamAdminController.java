package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.dto.exam.ExamImportPreviewReport;
import com.nihon.dto.exam.ExamPaperForm;
import com.nihon.dto.exam.ExamQuestionForm;
import com.nihon.dto.exam.ExamSectionForm;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import com.nihon.service.exam.ExamExcelService;
import com.nihon.service.exam.ExamService;
import com.nihon.service.excel.RateLimitExceededException;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Controller quản trị Đề thi, Phần thi, Câu hỏi và Nhập/Xuất Excel (F41).
 * Không có quyền sửa hoặc xóa bài làm của học viên.
 * Bảo vệ mạng qua ImportSecurityInterceptor cho các route /exams/admin/import/**.
 */
@Controller
@RequestMapping("/exams/admin")
public class ExamAdminController {

    private static final Logger log = LoggerFactory.getLogger(ExamAdminController.class);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ExamService examService;
    private final ExamExcelService examExcelService;
    private final TopicService topicService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;
    private final ResourceLoader resourceLoader;

    public ExamAdminController(ExamService examService,
                               ExamExcelService examExcelService,
                               TopicService topicService,
                               ClientIpResolver clientIpResolver,
                               RateLimiterService rateLimiterService,
                               ResourceLoader resourceLoader) {
        this.examService = examService;
        this.examExcelService = examExcelService;
        this.topicService = topicService;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiterService = rateLimiterService;
        this.resourceLoader = resourceLoader;
    }

    // =========================================================================
    // 1. Danh sách đề thi (List & Filter)
    // =========================================================================

    @GetMapping
    public String list(@RequestParam(value = "category", required = false) String categoryStr,
                       @RequestParam(value = "published", required = false) Boolean published,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));

        ExamCategory category = ExamCategory.isValid(categoryStr) ? ExamCategory.fromString(categoryStr) : null;
        Page<ExamPaper> examsPage = examService.searchExams(category, published, keyword, pageable);

        model.addAttribute("examsPage", examsPage);
        model.addAttribute("categories", ExamCategory.values());
        model.addAttribute("selectedCategory", category != null ? category.name() : "");
        model.addAttribute("selectedPublished", published);
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");
        model.addAttribute("currentNav", "exams");
        return "exams/admin/list";
    }

    // =========================================================================
    // 2. Tạo & Sửa Đề thi
    // =========================================================================

    @GetMapping("/new")
    public String newExamForm(Model model) {
        ExamPaperForm form = ExamPaperForm.builder()
                .durationMinutes(60)
                .category(ExamCategory.REVIEW.name())
                .published(false)
                .build();
        model.addAttribute("form", form);
        model.addAttribute("categories", ExamCategory.values());
        model.addAttribute("topics", getTopicsList());
        model.addAttribute("isEdit", false);
        model.addAttribute("currentNav", "exams");
        return "exams/admin/paper-form";
    }

    @PostMapping("/new")
    public String createExam(@Valid @ModelAttribute("form") ExamPaperForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", ExamCategory.values());
            model.addAttribute("topics", getTopicsList());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/paper-form";
        }

        try {
            ExamPaper paper = examService.createExam(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo thành công đề thi: " + paper.getTitle() + " (" + paper.getCode() + ").");
            return "redirect:/exams/admin/" + paper.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", ExamCategory.values());
            model.addAttribute("topics", getTopicsList());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/paper-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editExamForm(@PathVariable("id") Long id, Model model) {
        ExamPaper paper = examService.getExamById(id);
        ExamPaperForm form = ExamPaperForm.fromEntity(paper);

        model.addAttribute("form", form);
        model.addAttribute("categories", ExamCategory.values());
        model.addAttribute("topics", getTopicsList());
        model.addAttribute("isEdit", true);
        model.addAttribute("currentNav", "exams");
        return "exams/admin/paper-form";
    }

    @PostMapping("/{id}/edit")
    public String updateExam(@PathVariable("id") Long id,
                             @Valid @ModelAttribute("form") ExamPaperForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", ExamCategory.values());
            model.addAttribute("topics", getTopicsList());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/paper-form";
        }

        try {
            ExamPaper paper = examService.updateExam(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật thông tin đề thi: " + paper.getTitle() + " (" + paper.getCode() + ").");
            return "redirect:/exams/admin/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", ExamCategory.values());
            model.addAttribute("topics", getTopicsList());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/paper-form";
        }
    }

    // =========================================================================
    // 3. Chi tiết Đề thi & Quản lý cấu trúc
    // =========================================================================

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        ExamPaper paper = examService.getExamWithDetails(id);
        model.addAttribute("paper", paper);
        model.addAttribute("newSectionForm", ExamSectionForm.builder().paperId(id).build());
        model.addAttribute("currentNav", "exams");
        return "exams/admin/detail";
    }

    @PostMapping("/{id}/publish")
    public String togglePublish(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        ExamPaper paper = examService.togglePublish(id);
        String status = paper.isPublished() ? "Xuất bản" : "Hủy xuất bản";
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã " + status + " đề thi '" + paper.getTitle() + "' thành công.");
        return "redirect:/exams/admin/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteExam(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        ExamPaper paper = examService.getExamWithDetails(id);
        int sectionCount = paper.getSections().size();
        int questionCount = paper.getTotalQuestions();
        String title = paper.getTitle();

        try {
            examService.deleteExam(id);
            redirectAttributes.addFlashAttribute("successMessage", String.format(
                    "Đã xóa đề thi '%s' cùng %d phần thi và %d câu hỏi liên quan thành công.",
                    title, sectionCount, questionCount
            ));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/exams/admin";
    }

    // =========================================================================
    // 4. Quản lý Phần thi (ExamSection)
    // =========================================================================

    @PostMapping("/{id}/sections")
    public String addSection(@PathVariable("id") Long id,
                             @Valid @ModelAttribute("newSectionForm") ExamSectionForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tiêu đề phần thi không được để trống và tối đa 255 ký tự.");
            return "redirect:/exams/admin/" + id;
        }

        try {
            examService.addSection(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm phần thi mới thành công.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/exams/admin/" + id;
    }

    @PostMapping("/sections/{sectionId}/edit")
    public String editSection(@PathVariable("sectionId") Long sectionId,
                              @RequestParam("paperId") Long paperId,
                              @RequestParam("title") String title,
                              @RequestParam(value = "instructions", required = false) String instructions,
                              RedirectAttributes redirectAttributes) {
        ExamSectionForm form = ExamSectionForm.builder()
                .paperId(paperId)
                .title(title)
                .instructions(instructions)
                .build();
        try {
            examService.updateSection(sectionId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật phần thi thành công.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/exams/admin/" + paperId;
    }

    @PostMapping("/sections/{sectionId}/delete")
    public String deleteSection(@PathVariable("sectionId") Long sectionId,
                                @RequestParam("paperId") Long paperId,
                                RedirectAttributes redirectAttributes) {
        examService.deleteSection(sectionId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa phần thi và các câu hỏi trong phần thành công.");
        return "redirect:/exams/admin/" + paperId;
    }

    @PostMapping("/sections/{sectionId}/reorder")
    public String reorderSection(@PathVariable("sectionId") Long sectionId,
                                 @RequestParam("paperId") Long paperId,
                                 @RequestParam("direction") String direction) {
        examService.moveSection(sectionId, "up".equalsIgnoreCase(direction));
        return "redirect:/exams/admin/" + paperId;
    }

    // =========================================================================
    // 5. Quản lý Câu hỏi (ExamQuestion)
    // =========================================================================

    @GetMapping("/sections/{sectionId}/questions/new")
    public String newQuestionForm(@PathVariable("sectionId") Long sectionId,
                                  @RequestParam(value = "type", required = false, defaultValue = "MULTIPLE_CHOICE") String typeStr,
                                  Model model) {
        ExamQuestionType type = ExamQuestionType.isValid(typeStr) ? ExamQuestionType.fromString(typeStr) : ExamQuestionType.MULTIPLE_CHOICE;
        ExamQuestionForm form = ExamQuestionForm.builder()
                .sectionId(sectionId)
                .type(type.name())
                .points(type == ExamQuestionType.ORDERING ? BigDecimal.valueOf(2.00) : BigDecimal.valueOf(1.00))
                .correctChoiceIndex(1)
                .build();

        model.addAttribute("form", form);
        model.addAttribute("types", ExamQuestionType.values());
        model.addAttribute("selectedType", type);
        model.addAttribute("sectionId", sectionId);
        model.addAttribute("isEdit", false);
        model.addAttribute("currentNav", "exams");
        return "exams/admin/question-form";
    }

    @PostMapping("/sections/{sectionId}/questions/new")
    public String createQuestion(@PathVariable("sectionId") Long sectionId,
                                 @RequestParam("paperId") Long paperId,
                                 @Valid @ModelAttribute("form") ExamQuestionForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        form.setSectionId(sectionId);
        ExamQuestionType type = ExamQuestionType.isValid(form.getType()) ? ExamQuestionType.fromString(form.getType()) : ExamQuestionType.MULTIPLE_CHOICE;

        if (bindingResult.hasErrors()) {
            model.addAttribute("types", ExamQuestionType.values());
            model.addAttribute("selectedType", type);
            model.addAttribute("sectionId", sectionId);
            model.addAttribute("paperId", paperId);
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/question-form";
        }

        try {
            examService.addQuestion(sectionId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm câu hỏi mới thành công.");
            return "redirect:/exams/admin/" + paperId;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("types", ExamQuestionType.values());
            model.addAttribute("selectedType", type);
            model.addAttribute("sectionId", sectionId);
            model.addAttribute("paperId", paperId);
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/question-form";
        }
    }

    @GetMapping("/questions/{questionId}/edit")
    public String editQuestionForm(@PathVariable("questionId") Long questionId,
                                   @RequestParam("paperId") Long paperId,
                                   Model model) {
        ExamQuestion question = examService.getQuestionById(questionId);
        ExamQuestionForm form = ExamQuestionForm.fromEntity(question);

        model.addAttribute("form", form);
        model.addAttribute("types", ExamQuestionType.values());
        model.addAttribute("selectedType", question.getType());
        model.addAttribute("sectionId", question.getSection().getId());
        model.addAttribute("paperId", paperId);
        model.addAttribute("isEdit", true);
        model.addAttribute("currentNav", "exams");
        return "exams/admin/question-form";
    }

    @PostMapping("/questions/{questionId}/edit")
    public String updateQuestion(@PathVariable("questionId") Long questionId,
                                 @RequestParam("paperId") Long paperId,
                                 @Valid @ModelAttribute("form") ExamQuestionForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        ExamQuestionType type = ExamQuestionType.isValid(form.getType()) ? ExamQuestionType.fromString(form.getType()) : ExamQuestionType.MULTIPLE_CHOICE;

        if (bindingResult.hasErrors()) {
            model.addAttribute("types", ExamQuestionType.values());
            model.addAttribute("selectedType", type);
            model.addAttribute("paperId", paperId);
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/question-form";
        }

        try {
            examService.updateQuestion(questionId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật câu hỏi thành công.");
            return "redirect:/exams/admin/" + paperId;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("types", ExamQuestionType.values());
            model.addAttribute("selectedType", type);
            model.addAttribute("paperId", paperId);
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/question-form";
        }
    }

    @PostMapping("/questions/{questionId}/delete")
    public String deleteQuestion(@PathVariable("questionId") Long questionId,
                                 @RequestParam("paperId") Long paperId,
                                 RedirectAttributes redirectAttributes) {
        examService.deleteQuestion(questionId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa câu hỏi thành công.");
        return "redirect:/exams/admin/" + paperId;
    }

    @PostMapping("/questions/{questionId}/reorder")
    public String reorderQuestion(@PathVariable("questionId") Long questionId,
                                  @RequestParam("paperId") Long paperId,
                                  @RequestParam("direction") String direction) {
        examService.moveQuestion(questionId, "up".equalsIgnoreCase(direction));
        return "redirect:/exams/admin/" + paperId;
    }

    // =========================================================================
    // 6. Xem trước đề thi hoàn chỉnh (Preview)
    // =========================================================================

    @GetMapping("/{id}/preview")
    public String previewExam(@PathVariable("id") Long id, Model model) {
        ExamPaper paper = examService.getExamWithDetails(id);
        model.addAttribute("paper", paper);
        model.addAttribute("currentNav", "exams");
        return "exams/admin/preview";
    }

    // =========================================================================
    // 7. Nhập đề thi từ Excel (/exams/admin/import)
    // =========================================================================

    @GetMapping("/import")
    public String showImportPage(Model model) {
        model.addAttribute("currentNav", "exams");
        return "exams/admin/import";
    }

    @PostMapping("/import/preview")
    public String previewImport(@RequestParam("file") MultipartFile file,
                                HttpServletRequest request,
                                Model model) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        try {
            ExamImportPreviewReport report = examExcelService.preview(file, clientIp);
            model.addAttribute("report", report);
            model.addAttribute("currentNav", "exams");
            return "exams/admin/import-preview";
        } catch (UploadRejectedException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("currentNav", "exams");
            return "exams/admin/import";
        } catch (Exception ex) {
            log.error("Lỗi xem trước tệp Excel đề thi: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Không thể đọc tệp Excel: " + ex.getMessage());
            model.addAttribute("currentNav", "exams");
            return "exams/admin/import";
        }
    }

    @PostMapping("/import/commit")
    public String commitImport(@RequestParam("token") String token,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        try {
            ExamPaper paper = examExcelService.commit(token, clientIp);
            redirectAttributes.addFlashAttribute("successMessage", String.format(
                    "Đã nhập thành công đề thi '%s' (Mã: %s) với %d câu hỏi.",
                    paper.getTitle(), paper.getCode(), paper.getTotalQuestions()
            ));
            return "redirect:/exams/admin/" + paper.getId();
        } catch (Exception ex) {
            log.error("Lỗi xác nhận nhập đề thi từ Excel: {}", ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/exams/admin/import";
        }
    }

    // =========================================================================
    // 8. Xuất đề thi ra Excel & Tải tệp mẫu
    // =========================================================================

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportExam(@PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải xuống (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        ExamPaper paper = examService.getExamById(id);
        byte[] bytes = examExcelService.exportExam(id);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String safeCode = paper.getCode().replaceAll("[^A-Za-z0-9_-]", "_");
        String filename = "nihon-exam-" + safeCode + "-" + timestamp + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = resourceLoader.getResource("classpath:static/template/ExamTemplate.xlsx");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ExamTemplate.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    private List<Topic> getTopicsList() {
        List<Topic> topics = topicService.findAllOrdered();
        return topics != null ? topics : Collections.emptyList();
    }
}
