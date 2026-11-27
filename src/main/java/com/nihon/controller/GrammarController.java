package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.dto.grammar.GrammarArenaQuestionDto;
import com.nihon.dto.grammar.GrammarArenaResultDto;
import com.nihon.dto.grammar.GrammarArenaSession;
import com.nihon.dto.grammar.GrammarArenaStartForm;
import com.nihon.dto.grammar.GrammarExampleForm;
import com.nihon.dto.grammar.GrammarImportPreviewReport;
import com.nihon.dto.grammar.GrammarProgressToggleResponse;
import com.nihon.dto.grammar.GrammarQuizCheckResult;
import com.nihon.dto.grammar.GrammarQuizChoiceForm;
import com.nihon.dto.grammar.GrammarQuizQuestionForm;
import com.nihon.dto.grammar.GrammarQuizSubmissionForm;
import com.nihon.dto.grammar.GrammarTopicForm;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarExample;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.entity.Topic;
import com.nihon.history.LearnerContext;
import com.nihon.service.TopicService;
import com.nihon.service.excel.RateLimitExceededException;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import com.nihon.service.grammar.GrammarExcelService;
import com.nihon.service.grammar.GrammarPracticeService;
import com.nihon.service.grammar.GrammarService;
import com.nihon.service.grammar.GrammarTextRenderer;
import com.nihon.validation.TextNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Controller quản trị kho Ngữ pháp và Kiến thức bổ sung (F30-F32).
 * Hỗ trợ danh sách lọc, tìm kiếm, phân trang, CRUD, sắp xếp, xem trước và Nhập/Xuất Excel.
 */
@Controller
@RequestMapping("/grammar")
public class GrammarController {

    private static final Logger log = LoggerFactory.getLogger(GrammarController.class);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final GrammarService grammarService;
    private final GrammarExcelService excelService;
    private final TopicService generalTopicService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;
    private final ResourceLoader resourceLoader;
    private final GrammarPracticeService practiceService;

    public GrammarController(GrammarService grammarService,
                             GrammarExcelService excelService,
                             TopicService generalTopicService,
                             ClientIpResolver clientIpResolver,
                             RateLimiterService rateLimiterService,
                             ResourceLoader resourceLoader,
                             GrammarPracticeService practiceService) {
        this.grammarService = grammarService;
        this.excelService = excelService;
        this.generalTopicService = generalTopicService;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiterService = rateLimiterService;
        this.resourceLoader = resourceLoader;
        this.practiceService = practiceService;
    }

    /**
     * Màn hình danh sách Ngữ pháp và Kiến thức bổ sung cho người học (F30, F31, F34, F35).
     * Dạng thẻ Accordion, phân trang 20 mục, lọc category, bài học, tìm kiếm, hiển thị thanh tiến độ.
     */
    @GetMapping
    public String index(@RequestParam(value = "category", required = false, defaultValue = "GRAMMAR") String categoryStr,
                        @RequestParam(value = "topicId", required = false) Long topicId,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        GrammarCategory category = null;
        if (categoryStr != null && !categoryStr.isBlank() && !"ALL".equalsIgnoreCase(categoryStr.trim())) {
            try {
                category = GrammarCategory.fromString(categoryStr);
            } catch (IllegalArgumentException ignored) {
                category = GrammarCategory.GRAMMAR;
            }
        }

        String safeKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            if (kw.length() > 50) {
                kw = kw.substring(0, 50);
            }
            if (!TextNormalizer.containsControlChars(kw)) {
                safeKeyword = kw;
            }
        }

        int validPage = Math.min(Math.max(0, page), 100_000);
        int pageSize = 20; // Yêu cầu phân trang 20
        Pageable pageable = PageRequest.of(validPage, pageSize, Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));

        Page<GrammarTopic> topicsPage = grammarService.getGrammarTopics(category, topicId, safeKeyword, pageable);

        // Nạp sẵn chi tiết (examples và questions) cho các topic trên trang hiện tại
        for (GrammarTopic t : topicsPage.getContent()) {
            if (t.getExamples() != null) {
                t.getExamples().size();
            }
            if (t.getQuestions() != null) {
                for (GrammarQuizQuestion q : t.getQuestions()) {
                    if (q.getChoices() != null) {
                        q.getChoices().size();
                    }
                }
            }
        }

        String learnerId = LearnerContext.current().orElse("");
        Set<Long> learnedGrammarIds = practiceService.getLearnedGrammarIds(learnerId);
        long learnedCount = practiceService.getLearnedCount(learnerId);
        int progressPercent = practiceService.calculateProgressPercent(learnerId);
        long totalTopics = grammarService.getAllTopics().size();

        model.addAttribute("topicsPage", topicsPage);
        model.addAttribute("selectedCategory", category != null ? category.name() : "GRAMMAR");
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("keyword", safeKeyword != null ? safeKeyword : "");
        model.addAttribute("generalTopics", generalTopicService.findAllOrdered());
        model.addAttribute("learnedGrammarIds", learnedGrammarIds);
        model.addAttribute("learnedCount", learnedCount);
        model.addAttribute("totalTopics", totalTopics);
        model.addAttribute("progressPercent", progressPercent);
        model.addAttribute("currentNav", "grammar");
        return "grammar/index";
    }

    /**
     * Đánh dấu hoặc hủy đánh dấu 'Đã học' cho chủ đề ngữ pháp (F30).
     */
    @PostMapping("/{id}/toggle-progress")
    public Object toggleProgress(@PathVariable("id") Long id,
                                 @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        String learnerId = LearnerContext.current()
                .orElseThrow(() -> new IllegalStateException("Không xác định được danh tính người học."));
        GrammarProgressToggleResponse res = practiceService.toggleProgress(id, learnerId);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok(res);
        }

        redirectAttributes.addFlashAttribute("successMessage", res.getMessage());
        return "redirect:" + (redirectUrl != null && !redirectUrl.isBlank() ? redirectUrl : "/grammar");
    }

    /**
     * Chấm bài trắc nghiệm mini tại thẻ ngữ pháp (F32).
     */
    @PostMapping("/{id}/quiz/check")
    public Object checkMiniQuiz(@PathVariable("id") Long id,
                                @RequestBody(required = false) GrammarQuizSubmissionForm jsonForm,
                                @ModelAttribute GrammarQuizSubmissionForm form,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        GrammarQuizSubmissionForm effectiveForm = (jsonForm != null && jsonForm.getGrammarId() != null) ? jsonForm : form;
        if (effectiveForm == null || effectiveForm.getGrammarId() == null) {
            effectiveForm = GrammarQuizSubmissionForm.builder().grammarId(id).build();
        }
        effectiveForm.setGrammarId(id);

        String learnerId = LearnerContext.current().orElse("");
        GrammarQuizCheckResult result = practiceService.checkMiniQuiz(effectiveForm, learnerId);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok(result);
        }

        redirectAttributes.addFlashAttribute("quizResult", result);
        return "redirect:/grammar#" + id;
    }

    // =========================================================================
    // Đấu trường phản xạ ngữ pháp (F33)
    // =========================================================================

    @GetMapping("/arena")
    public String arenaConfig(HttpSession session, Model model) {
        GrammarArenaSession active = practiceService.getActiveArenaSession(session);
        model.addAttribute("activeSession", active);
        model.addAttribute("generalTopics", generalTopicService.findAllOrdered());
        model.addAttribute("startForm", GrammarArenaStartForm.builder()
                .category("ALL")
                .durationMinutes(5)
                .questionCount(10)
                .build());
        model.addAttribute("currentNav", "grammar");
        return "grammar/arena/index";
    }

    @PostMapping("/arena/start")
    public String arenaStart(@Valid @ModelAttribute("startForm") GrammarArenaStartForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", firstError != null ? firstError : "Cấu hình đấu trường không hợp lệ.");
            return "redirect:/grammar/arena";
        }
        try {
            practiceService.startArena(form, session);
            return "redirect:/grammar/arena/run";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/grammar/arena";
        }
    }

    @GetMapping("/arena/run")
    public String arenaRun(HttpSession session, Model model) {
        GrammarArenaQuestionDto question = practiceService.getCurrentArenaQuestion(session);
        if (question == null) {
            return "redirect:/grammar/arena/result";
        }
        model.addAttribute("question", question);
        model.addAttribute("currentNav", "grammar");
        return "grammar/arena/run";
    }

    @PostMapping("/arena/answer")
    public String arenaAnswer(@RequestParam(value = "choiceId", required = false) Long choiceId,
                              HttpSession session) {
        String learnerId = LearnerContext.current().orElse("");
        practiceService.submitArenaAnswer(choiceId, session, learnerId);
        GrammarArenaSession active = practiceService.getActiveArenaSession(session);
        if (active != null && active.isRunning()) {
            return "redirect:/grammar/arena/run";
        }
        return "redirect:/grammar/arena/result";
    }

    @PostMapping("/arena/finish")
    public String arenaFinish(HttpSession session) {
        String learnerId = LearnerContext.current().orElse("");
        practiceService.finishArenaSession(session, learnerId);
        return "redirect:/grammar/arena/result";
    }

    @GetMapping("/arena/result")
    public String arenaResult(HttpSession session, Model model) {
        GrammarArenaResultDto result = practiceService.getLastArenaResult(session);
        if (result == null) {
            return "redirect:/grammar/arena";
        }
        model.addAttribute("result", result);
        model.addAttribute("currentNav", "grammar");
        return "grammar/arena/result";
    }

    // =========================================================================
    // 1. Danh sách chủ đề ngữ pháp (/grammar/admin)
    // =========================================================================

    @GetMapping("/admin")
    public String listTopics(@RequestParam(value = "category", required = false) String categoryStr,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "size", defaultValue = "10") int size,
                             Model model) {
        GrammarCategory category = null;
        if (categoryStr != null && !categoryStr.isBlank() && !"ALL".equalsIgnoreCase(categoryStr.trim())) {
            try {
                category = GrammarCategory.fromString(categoryStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        int validPage = Math.min(Math.max(0, page), 100_000);
        int validSize = (size > 0 && size <= 100) ? size : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("sortOrder").ascending().and(Sort.by("id").ascending()));

        Page<GrammarTopic> topicsPage = grammarService.getGrammarTopics(category, keyword, pageable);

        model.addAttribute("topicsPage", topicsPage);
        model.addAttribute("selectedCategory", categoryStr != null ? categoryStr : "ALL");
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", GrammarCategory.values());
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/list";
    }

    // =========================================================================
    // 2. Thêm mới chủ đề ngữ pháp
    // =========================================================================

    @GetMapping("/admin/create")
    public String createTopicForm(Model model) {
        model.addAttribute("form", new GrammarTopicForm());
        model.addAttribute("categories", GrammarCategory.values());
        model.addAttribute("generalTopics", getGeneralTopics());
        model.addAttribute("isEdit", false);
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/form";
    }

    @PostMapping("/admin/create")
    public String createTopic(@Valid @ModelAttribute("form") GrammarTopicForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", GrammarCategory.values());
            model.addAttribute("generalTopics", getGeneralTopics());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/form";
        }

        try {
            GrammarTopic created = grammarService.createTopic(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo thành công chủ đề ngữ pháp '" + created.getTitle() + "' (Mã: " + created.getCode() + ").");
            return "redirect:/grammar/admin/" + created.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", GrammarCategory.values());
            model.addAttribute("generalTopics", getGeneralTopics());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/form";
        }
    }

    // =========================================================================
    // 3. Chi tiết & Quản lý nội dung chủ đề
    // =========================================================================

    @GetMapping("/admin/{id}")
    public String detailTopic(@PathVariable("id") Long id, Model model) {
        GrammarTopic topic = grammarService.getTopicWithDetails(id);
        String renderedExplanation = GrammarTextRenderer.renderExplanation(topic.getExplanation());

        model.addAttribute("topic", topic);
        model.addAttribute("renderedExplanation", renderedExplanation);
        model.addAttribute("exampleForm", new GrammarExampleForm());
        model.addAttribute("questionForm", new GrammarQuizQuestionForm());
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/detail";
    }

    // =========================================================================
    // 4. Chỉnh sửa chủ đề
    // =========================================================================

    @GetMapping("/admin/{id}/edit")
    public String editTopicForm(@PathVariable("id") Long id, Model model) {
        GrammarTopic topic = grammarService.getTopicById(id);
        GrammarTopicForm form = GrammarTopicForm.fromEntity(topic);

        model.addAttribute("form", form);
        model.addAttribute("categories", GrammarCategory.values());
        model.addAttribute("generalTopics", getGeneralTopics());
        model.addAttribute("isEdit", true);
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/form";
    }

    @PostMapping("/admin/{id}/edit")
    public String updateTopic(@PathVariable("id") Long id,
                              @Valid @ModelAttribute("form") GrammarTopicForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", GrammarCategory.values());
            model.addAttribute("generalTopics", getGeneralTopics());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/form";
        }

        try {
            GrammarTopic updated = grammarService.updateTopic(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật chủ đề ngữ pháp '" + updated.getTitle() + "'.");
            return "redirect:/grammar/admin/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("categories", GrammarCategory.values());
            model.addAttribute("generalTopics", getGeneralTopics());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/form";
        }
    }

    // =========================================================================
    // 5. Xóa chủ đề (Cascade ví dụ & câu hỏi)
    // =========================================================================

    @PostMapping("/admin/{id}/delete")
    public String deleteTopic(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            grammarService.deleteTopic(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa chủ đề ngữ pháp thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa chủ đề: " + e.getMessage());
        }
        return "redirect:/grammar/admin";
    }

    // =========================================================================
    // 6. Xem trước giao diện người học (Preview)
    // =========================================================================

    @GetMapping("/admin/{id}/preview")
    public String previewTopic(@PathVariable("id") Long id, Model model) {
        GrammarTopic topic = grammarService.getTopicWithDetails(id);
        String renderedExplanation = GrammarTextRenderer.renderExplanation(topic.getExplanation());

        model.addAttribute("topic", topic);
        model.addAttribute("renderedExplanation", renderedExplanation);
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/preview";
    }

    // =========================================================================
    // 7. Quản lý Ví dụ (GrammarExample)
    // =========================================================================

    @PostMapping("/admin/{id}/examples")
    public String addExample(@PathVariable("id") Long topicId,
                             @Valid @ModelAttribute("exampleForm") GrammarExampleForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", error != null ? error : "Dữ liệu ví dụ không hợp lệ.");
            return "redirect:/grammar/admin/" + topicId;
        }

        try {
            grammarService.addExample(topicId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm câu ví dụ mới thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/grammar/admin/" + topicId;
    }

    @PostMapping("/admin/examples/{exampleId}/edit")
    public String updateExample(@PathVariable("exampleId") Long exampleId,
                                @RequestParam("topicId") Long topicId,
                                @Valid @ModelAttribute("exampleForm") GrammarExampleForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", error != null ? error : "Dữ liệu ví dụ không hợp lệ.");
            return "redirect:/grammar/admin/" + topicId;
        }

        try {
            grammarService.updateExample(exampleId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật câu ví dụ thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/grammar/admin/" + topicId;
    }

    @PostMapping("/admin/examples/{exampleId}/delete")
    public String deleteExample(@PathVariable("exampleId") Long exampleId,
                                @RequestParam("topicId") Long topicId,
                                RedirectAttributes redirectAttributes) {
        try {
            grammarService.deleteExample(exampleId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa câu ví dụ.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/grammar/admin/" + topicId;
    }

    @PostMapping("/admin/examples/{exampleId}/reorder")
    public String reorderExample(@PathVariable("exampleId") Long exampleId,
                                 @RequestParam("topicId") Long topicId,
                                 @RequestParam("direction") String direction) {
        grammarService.moveExample(exampleId, "up".equalsIgnoreCase(direction));
        return "redirect:/grammar/admin/" + topicId;
    }

    // =========================================================================
    // 8. Quản lý Câu hỏi Trắc nghiệm (GrammarQuizQuestion)
    // =========================================================================

    @GetMapping("/admin/{id}/questions/create")
    public String createQuestionForm(@PathVariable("id") Long topicId, Model model) {
        GrammarTopic topic = grammarService.getTopicById(topicId);
        GrammarQuizQuestionForm form = new GrammarQuizQuestionForm();
        List<GrammarQuizChoiceForm> choices = new ArrayList<>();
        choices.add(GrammarQuizChoiceForm.builder().sortOrder(1).text("").correct(true).build());
        choices.add(GrammarQuizChoiceForm.builder().sortOrder(2).text("").correct(false).build());
        choices.add(GrammarQuizChoiceForm.builder().sortOrder(3).text("").correct(false).build());
        choices.add(GrammarQuizChoiceForm.builder().sortOrder(4).text("").correct(false).build());
        form.setChoices(choices);

        model.addAttribute("topic", topic);
        model.addAttribute("form", form);
        model.addAttribute("isEdit", false);
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/question-form";
    }

    @PostMapping("/admin/{id}/questions/create")
    public String createQuestion(@PathVariable("id") Long topicId,
                                 @Valid @ModelAttribute("form") GrammarQuizQuestionForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topic", grammarService.getTopicById(topicId));
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/question-form";
        }

        try {
            grammarService.addQuestion(topicId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm câu hỏi trắc nghiệm mới.");
            return "redirect:/grammar/admin/" + topicId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("topic", grammarService.getTopicById(topicId));
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/question-form";
        }
    }

    @GetMapping("/admin/questions/{qId}/edit")
    public String editQuestionForm(@PathVariable("qId") Long qId,
                                   @RequestParam("topicId") Long topicId,
                                   Model model) {
        GrammarTopic topic = grammarService.getTopicById(topicId);
        GrammarQuizQuestion question = grammarService.getQuestionById(qId);
        GrammarQuizQuestionForm form = GrammarQuizQuestionForm.fromEntity(question);

        model.addAttribute("topic", topic);
        model.addAttribute("form", form);
        model.addAttribute("isEdit", true);
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/question-form";
    }

    @PostMapping("/admin/questions/{qId}/edit")
    public String updateQuestion(@PathVariable("qId") Long qId,
                                 @RequestParam("topicId") Long topicId,
                                 @Valid @ModelAttribute("form") GrammarQuizQuestionForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topic", grammarService.getTopicById(topicId));
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/question-form";
        }

        try {
            grammarService.updateQuestion(qId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật câu hỏi trắc nghiệm.");
            return "redirect:/grammar/admin/" + topicId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("topic", grammarService.getTopicById(topicId));
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/question-form";
        }
    }

    @PostMapping("/admin/questions/{qId}/delete")
    public String deleteQuestion(@PathVariable("qId") Long qId,
                                 @RequestParam("topicId") Long topicId,
                                 RedirectAttributes redirectAttributes) {
        try {
            grammarService.deleteQuestion(qId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa câu hỏi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/grammar/admin/" + topicId;
    }

    @PostMapping("/admin/questions/{qId}/reorder")
    public String reorderQuestion(@PathVariable("qId") Long qId,
                                  @RequestParam("topicId") Long topicId,
                                  @RequestParam("direction") String direction) {
        grammarService.moveQuestion(qId, "up".equalsIgnoreCase(direction));
        return "redirect:/grammar/admin/" + topicId;
    }

    // =========================================================================
    // 9. Nhập Excel hai bước (/grammar/admin/import)
    // =========================================================================

    @GetMapping("/admin/import")
    public String showImportPage(Model model) {
        model.addAttribute("currentNav", "grammar");
        return "grammar/admin/import";
    }

    @PostMapping("/admin/import/preview")
    public String previewImport(@RequestParam("file") MultipartFile file,
                                HttpServletRequest request,
                                Model model) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        try {
            GrammarImportPreviewReport report = excelService.preview(file, clientIp);
            model.addAttribute("report", report);
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/import-preview";
        } catch (UploadRejectedException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/import";
        } catch (Exception ex) {
            log.error("Lỗi xem trước tệp Excel ngữ pháp: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Không thể đọc tệp Excel: " + ex.getMessage());
            model.addAttribute("currentNav", "grammar");
            return "grammar/admin/import";
        }
    }

    @PostMapping("/admin/import/commit")
    public String commitImport(@RequestParam("token") String token,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải lên (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        try {
            int totalSaved = excelService.commit(token, clientIp);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã nhập thành công " + totalSaved + " chủ đề ngữ pháp từ tệp Excel.");
            return "redirect:/grammar/admin";
        } catch (Exception ex) {
            log.error("Lỗi xác nhận nhập tệp Excel ngữ pháp: {}", ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/grammar/admin/import";
        }
    }

    // =========================================================================
    // 10. Xuất Excel & Tải tệp mẫu (/grammar/admin/export, /template)
    // =========================================================================

    @GetMapping("/admin/export")
    public ResponseEntity<byte[]> exportGrammar(@RequestParam(value = "category", required = false) String catStr,
                                                HttpServletRequest request) throws IOException {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!rateLimiterService.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải xuống (tối đa 10 lần mỗi phút). Vui lòng thử lại sau.");
        }

        GrammarCategory cat = null;
        if (catStr != null && !catStr.isBlank() && !"ALL".equalsIgnoreCase(catStr)) {
            try {
                cat = GrammarCategory.fromString(catStr);
            } catch (Exception ignored) {
            }
        }

        byte[] bytes = excelService.exportGrammar(cat);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String filename = "nihon-grammar-" + (cat != null ? cat.name().toLowerCase() : "all") + "-" + timestamp + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/admin/template")
    public ResponseEntity<Resource> downloadTemplate() {
        Resource resource = resourceLoader.getResource("classpath:static/template/GrammarTemplate.xlsx");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GrammarTemplate.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    private List<Topic> getGeneralTopics() {
        List<Topic> topics = generalTopicService.findAllOrdered();
        return topics != null ? topics : Collections.emptyList();
    }
}

