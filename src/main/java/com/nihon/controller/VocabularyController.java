package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.dto.ExportTextFormat;
import com.nihon.dto.VocabQuickFilter;
import com.nihon.dto.VocabViewMode;
import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyExportRateLimiter;
import com.nihon.service.VocabularyExportService;
import com.nihon.service.VocabularyService;
import com.nihon.service.excel.RateLimitExceededException;
import com.nihon.validation.KanjiLimits;
import com.nihon.validation.TextNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/vocabulary")
public class VocabularyController {

    private static final int MAX_PAGE = 100000;
    private static final int MAX_KEYWORD_LENGTH = 50;
    public static final String KANJI_MISSING_READING_WARNING =
            "Từ có Kanji nhưng chưa có cách đọc, phần luyện tập sẽ không kiểm tra cách đọc.";

    private final VocabularyService vocabularyService;
    private final TopicService topicService;
    private final VocabularyExportService exportService;
    private final VocabularyExportRateLimiter exportRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public VocabularyController(VocabularyService vocabularyService, TopicService topicService) {
        this(vocabularyService, topicService, null, null, null);
    }

    @Autowired
    public VocabularyController(VocabularyService vocabularyService,
                                TopicService topicService,
                                @Autowired(required = false) VocabularyExportService exportService,
                                @Autowired(required = false) VocabularyExportRateLimiter exportRateLimiter,
                                @Autowired(required = false) ClientIpResolver clientIpResolver) {
        this.vocabularyService = vocabularyService;
        this.topicService = topicService;
        this.exportService = (exportService != null) ? exportService : new VocabularyExportService();
        this.exportRateLimiter = (exportRateLimiter != null) ? exportRateLimiter : new VocabularyExportRateLimiter();
        this.clientIpResolver = (clientIpResolver != null) ? clientIpResolver : new ClientIpResolver("127.0.0.1,::1,0:0:0:0:0:0:0:1");
    }

    @InitBinder("form")
    public void initFormBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(@RequestParam(value = "topicId", required = false) Long topicId,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "filter", defaultValue = "ALL") String filter,
                       @RequestParam(value = "view", defaultValue = "COMPACT") String view,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       Model model) {
        int safePage = Math.max(0, Math.min(MAX_PAGE, page));
        int safeSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").descending());

        String safeKeyword = sanitizeKeyword(keyword);
        VocabViewMode viewMode = VocabViewMode.fromString(view);
        VocabQuickFilter quickFilter = VocabQuickFilter.fromString(filter);
        List<Topic> topics = topicService.findAllOrdered();

        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicService.findById(topicId).isPresent();
            if (!topicExists) {
                model.addAttribute("errorMessage", "Chủ đề không tồn tại");
                Page<VocabularyRowDto> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                model.addAttribute("vocabPage", emptyPage);
                model.addAttribute("topics", topics);
                model.addAttribute("selectedTopicId", topicId);
                model.addAttribute("keyword", safeKeyword);
                model.addAttribute("filter", quickFilter.name());
                model.addAttribute("view", viewMode.name());
                model.addAttribute("quickFilters", VocabQuickFilter.values());
                return "vocabulary/list";
            }
        }

        Page<VocabularyRowDto> vocabPage;
        if (quickFilter == VocabQuickFilter.ALL && viewMode == VocabViewMode.COMPACT) {
            vocabPage = vocabularyService.search(effectiveTopicId, safeKeyword, pageable);
        } else {
            vocabPage = vocabularyService.search(effectiveTopicId, safeKeyword, quickFilter, viewMode, pageable);
        }
        if (vocabPage == null) {
            vocabPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String selectedTopicName = null;
        if (effectiveTopicId != null) {
            final Long targetTopicId = effectiveTopicId;
            selectedTopicName = topics.stream()
                    .filter(t -> targetTopicId.equals(t.getId()))
                    .map(Topic::getName)
                    .findFirst()
                    .orElseGet(() -> topicService.findById(targetTopicId).map(Topic::getName).orElse(null));
        }

        model.addAttribute("vocabPage", vocabPage);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("selectedTopicName", selectedTopicName);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("filter", quickFilter.name());
        model.addAttribute("view", viewMode.name());
        model.addAttribute("quickFilters", VocabQuickFilter.values());

        return "vocabulary/list";
    }

    /**
     * Xuất danh sách từ vựng đang lọc thành tệp văn bản thuần (.txt) cho Quizlet hoặc Anki.
     *
     * @param topicId ID chủ đề cần lọc (tùy chọn)
     * @param keyword Từ khóa tìm kiếm (tùy chọn, tối đa 50 ký tự)
     * @param format Định dạng phân cách (TAB hoặc COLON, whitelist)
     * @param request Yêu cầu HTTP để trích xuất IP phục vụ giới hạn tần suất
     * @return Tệp văn bản đính kèm UTF-8 có BOM
     */
    @GetMapping("/export-text")
    public ResponseEntity<byte[]> exportText(@RequestParam(value = "topicId", required = false) Long topicId,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "format", defaultValue = "TAB") String format,
                                            HttpServletRequest request) {
        // 1. Kiểm tra giới hạn tần suất (tối đa 20 lần/phút theo IP)
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!exportRateLimiter.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải xuống danh sách từ vựng (tối đa 20 lần mỗi phút). Vui lòng thử lại sau.");
        }

        // 2. Chuẩn hóa format (whitelist) và keyword
        ExportTextFormat exportFormat = ExportTextFormat.fromString(format);
        String safeKeyword = sanitizeKeyword(keyword);

        // 3. Kiểm tra topicId: nếu topicId không tồn tại hoặc không hợp lệ, trả tệp rỗng (không gây lỗi 500)
        List<VocabularyRowDto> rows;
        if (topicId != null) {
            if (topicId <= 0 || topicService.findById(topicId).isEmpty()) {
                rows = Collections.emptyList();
            } else {
                rows = vocabularyService.findAllMatching(topicId, safeKeyword);
            }
        } else {
            rows = vocabularyService.findAllMatching(null, safeKeyword);
        }

        // 4. Giới hạn tối đa 5000 dòng: nếu vượt quá thì trả lỗi HTTP 400 Bad Request
        if (rows.size() > VocabularyExportService.MAX_EXPORT_LIMIT) {
            byte[] errorBytes = "Số lượng từ vựng xuất khẩu vượt quá giới hạn tối đa cho phép (5000 dòng). Vui lòng thu hẹp bộ lọc theo chủ đề hoặc từ khóa.".getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(errorBytes);
        }

        // 5. Sinh nội dung tệp văn bản kèm BOM UTF-8
        byte[] fileBytes = exportService.exportToText(rows, exportFormat);

        // 6. Tên tệp cố định theo mẫu thời gian yyyyMMdd-HHmm, tuyệt đối không phụ thuộc input
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String filename = "nihon-vocabulary-" + timestamp + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(fileBytes);
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed;
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(value = "topicId", required = false) Long topicId,
                                 @RequestParam(value = "japaneseText", required = false) String japaneseText,
                                 Model model) {
        List<Topic> topics = topicService.findAllOrdered();
        if (!model.containsAttribute("form")) {
            VocabularyForm form = new VocabularyForm();
            if (topicId != null) {
                if (topicId <= 0) {
                    model.addAttribute("errorMessage", "ID không hợp lệ");
                } else {
                    boolean topicExists = (topics != null && topics.stream().anyMatch(t -> topicId.equals(t.getId())))
                            || topicService.findById(topicId).isPresent();
                    if (topicExists) {
                        form.setTopicId(topicId);
                    }
                }
            }
            if (japaneseText != null && !TextNormalizer.containsControlChars(japaneseText)) {
                String normalizedJp = TextNormalizer.normalize(japaneseText);
                if (normalizedJp != null && normalizedJp.length() <= KanjiLimits.MAX_JAPANESE_LENGTH) {
                    form.setJapaneseText(normalizedJp);
                }
            }
            model.addAttribute("form", form);
        }
        model.addAttribute("topics", topics);
        model.addAttribute("isEdit", false);
        return "vocabulary/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") VocabularyForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", false);
            return "vocabulary/form";
        }

        try {
            Vocabulary saved = vocabularyService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm từ vựng mới thành công!");
            if (saved != null && saved.isHasKanji() && (saved.getReading() == null || saved.getReading().isBlank())) {
                redirectAttributes.addFlashAttribute("warningMessage", KANJI_MISSING_READING_WARNING);
            }
            return "redirect:/vocabulary";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("topicId", "error.topic", e.getMessage());
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", false);
            return "vocabulary/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id,
                               @RequestParam(value = "scope", required = false) String scope,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/vocabulary";
        }
        return vocabularyService.findById(id).map(v -> {
            if (!model.containsAttribute("form")) {
                VocabularyForm form = vocabularyService.toForm(v);
                if (form != null) {
                    form.setScope("TOPIC".equalsIgnoreCase(scope) ? "TOPIC" : "ALL");
                    if (form.getAllIds() == null || form.getAllIds().isEmpty()) {
                        form.setAllIds(vocabularyService.findGroupIds(id));
                    }
                }
                model.addAttribute("form", form);
            }
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("vocabId", id);
            return "vocabulary/form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng yêu cầu (ID không hợp lệ).");
            return "redirect:/vocabulary";
        });
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("form") VocabularyForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/vocabulary";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("vocabId", id);
            return "vocabulary/form";
        }

        try {
            boolean hasGroupParams = (form.getScope() != null && !form.getScope().isBlank())
                    || (form.getAllIds() != null && !form.getAllIds().isEmpty());
            if (!hasGroupParams) {
                Vocabulary saved = vocabularyService.update(id, form);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật từ vựng thành công!");
                if (saved != null && saved.isHasKanji() && (saved.getReading() == null || saved.getReading().isBlank())) {
                    redirectAttributes.addFlashAttribute("warningMessage", KANJI_MISSING_READING_WARNING);
                }
            } else {
                List<Vocabulary> updatedList = vocabularyService.updateGroup(id, form);
                int updatedCount = (updatedList != null) ? updatedList.size() : 0;
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã cập nhật " + updatedCount + " bản ghi từ vựng thành công!");
                Vocabulary rep = (updatedList != null && !updatedList.isEmpty()) ? updatedList.get(0) : null;
                if (rep != null && rep.isHasKanji() && (rep.getReading() == null || rep.getReading().isBlank())) {
                    redirectAttributes.addFlashAttribute("warningMessage", KANJI_MISSING_READING_WARNING);
                }
            }
            return "redirect:/vocabulary";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng để cập nhật.");
            return "redirect:/vocabulary";
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("topicId", "error.topic", e.getMessage());
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("vocabId", id);
            return "vocabulary/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         @RequestParam(value = "scope", required = false) String scope,
                         @RequestParam(value = "allIds", required = false) List<Long> allIds,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/vocabulary";
        }
        try {
            boolean hasScope = scope != null && !scope.isBlank();
            boolean hasAllIds = allIds != null && !allIds.isEmpty();
            if (!hasScope && !hasAllIds) {
                vocabularyService.delete(id);
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa từ vựng thành công.");
            } else if ("TOPIC".equalsIgnoreCase(scope)) {
                int deletedCount = vocabularyService.deleteInTopic(id);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã xóa " + deletedCount + " bản ghi từ vựng thành công.");
            } else {
                int deletedCount = vocabularyService.deleteGroup(id, allIds);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã xóa " + deletedCount + " bản ghi từ vựng thành công.");
            }
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng cần xóa (ID không hợp lệ).");
        }
        return "redirect:/vocabulary";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/vocabulary";
        }
        return vocabularyService.findDetailById(id).map(detail -> {
            model.addAttribute("vocab", detail);
            return "vocabulary/detail";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng yêu cầu (ID không hợp lệ).");
            return "redirect:/vocabulary";
        });
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam(value = "ids", required = false) List<String> rawIds,
                             @RequestParam(value = "scope", required = false) String scope,
                             RedirectAttributes redirectAttributes) {
        List<Long> parsedIds = parseBulkIds(rawIds);
        String effectiveScope = "ALL".equalsIgnoreCase(scope) ? "ALL" : "TOPIC";
        try {
            VocabularyService.BulkDeleteResult result = vocabularyService.bulkDelete(parsedIds, effectiveScope);
            if (result != null) {
                if (result.skippedCount() > 0) {
                    redirectAttributes.addFlashAttribute("successMessage",
                            String.format("Đã xóa %d bản ghi từ vựng (bỏ qua %d ID không hợp lệ hoặc không tồn tại).",
                                    result.deletedCount(), result.skippedCount()));
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                            String.format("Đã xóa %d bản ghi từ vựng thành công.", result.deletedCount()));
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã xóa các từ vựng đã chọn thành công.");
            }
        } catch (IllegalArgumentException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/vocabulary";
    }

    @PostMapping("/bulk-move")
    public String bulkMove(@RequestParam(value = "ids", required = false) List<String> rawIds,
                           @RequestParam(value = "targetTopicId", required = false) Long targetTopicId,
                           @RequestParam(value = "scope", required = false) String scope,
                           RedirectAttributes redirectAttributes) {
        List<Long> parsedIds = parseBulkIds(rawIds);
        String effectiveScope = "ALL".equalsIgnoreCase(scope) ? "ALL" : "TOPIC";
        try {
            VocabularyService.BulkMoveResult result = vocabularyService.bulkMove(parsedIds, targetTopicId, effectiveScope);
            if (result != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Đã chuyển %d bản ghi từ vựng, gộp %d bản ghi trùng (bỏ qua %d ID không hợp lệ).",
                                result.movedCount(), result.mergedCount(), result.skippedCount()));
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển các từ vựng đã chọn sang chủ đề đích.");
            }
        } catch (IllegalArgumentException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/vocabulary";
    }

    private List<Long> parseBulkIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> parsed = new ArrayList<>();
        for (String item : rawIds) {
            if (item == null) {
                continue;
            }
            for (String token : item.split(",")) {
                String trimmed = token.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    parsed.add(-1L);
                }
            }
        }
        return parsed;
    }
}
