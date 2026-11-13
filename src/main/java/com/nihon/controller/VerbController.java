package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.conjugation.TargetForm;
import com.nihon.conjugation.VerbConjugator;
import com.nihon.dto.verb.VerbBulkForm;
import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.dto.verb.VerbLabResultDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.service.TopicService;
import com.nihon.service.VerbService;
import com.nihon.service.VocabularyExportRateLimiter;
import com.nihon.service.excel.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/verbs")
public class VerbController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE = 100000;
    private static final int MAX_PAGE_SIZE = 100;

    private final VerbService verbService;
    private final TopicService topicService;
    private final VocabularyExportRateLimiter exportRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public VerbController(VerbService verbService,
                          TopicService topicService,
                          VocabularyExportRateLimiter exportRateLimiter,
                          ClientIpResolver clientIpResolver) {
        this.verbService = verbService;
        this.topicService = topicService;
        this.exportRateLimiter = exportRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @InitBinder("form")
    public void initFormBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(@RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "group", required = false) Integer group,
                       @RequestParam(value = "topicId", required = false) Long topicId,
                       @RequestParam(value = "view", required = false) String view,
                       Model model) {
        int safePage = Math.max(0, Math.min(MAX_PAGE, page));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").ascending());
        Page<Verb> verbsPage = verbService.search(group, topicId, keyword, pageable);
        List<Topic> topics = topicService.findAllOrdered();

        String safeView = "TABLE5".equalsIgnoreCase(view) ? "TABLE5" : "COMPACT";
        List<VerbDetailDto> table5Verbs = Collections.emptyList();
        if ("TABLE5".equals(safeView)) {
            table5Verbs = verbsPage.getContent().stream()
                    .map(v -> VerbDetailDto.fromEntity(v, VerbConjugator.conjugate(v.getDictionaryForm(), v.getVerbGroup(), v.getKanjiText())))
                    .toList();
        }

        model.addAttribute("verbs", verbsPage);
        model.addAttribute("table5Verbs", table5Verbs);
        model.addAttribute("currentView", safeView);
        model.addAttribute("topics", topics);
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");
        model.addAttribute("selectedGroup", group);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("totalCount", verbsPage.getTotalElements());
        model.addAttribute("currentNav", "verbs");

        return "verbs/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID động từ không hợp lệ.");
            return "redirect:/verbs";
        }
        try {
            VerbDetailDto verbDetail = verbService.getDetailById(id);
            model.addAttribute("verb", verbDetail);
            model.addAttribute("currentNav", "verbs");
            return "verbs/detail";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/verbs";
        }
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new VerbForm());
        model.addAttribute("topics", topicService.findAllOrdered());
        model.addAttribute("isEdit", false);
        model.addAttribute("currentNav", "verbs");
        return "verbs/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") VerbForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "verbs");
            return "verbs/form";
        }

        try {
            Verb created = verbService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm động từ thành công!");
            return "redirect:/verbs/" + created.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", false);
            model.addAttribute("currentNav", "verbs");
            return "verbs/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID động từ không hợp lệ.");
            return "redirect:/verbs";
        }
        try {
            Verb verb = verbService.getById(id);
            VerbForm form = VerbForm.builder()
                    .id(verb.getId())
                    .topicId(verb.getTopic() != null ? verb.getTopic().getId() : null)
                    .masuForm(verb.getMasuForm())
                    .dictionaryForm(verb.getDictionaryForm())
                    .kanjiText(verb.getKanjiText())
                    .verbGroup(verb.getVerbGroup())
                    .meaningVi(verb.getMeaningVi())
                    .build();

            model.addAttribute("form", form);
            model.addAttribute("verbId", id);
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "verbs");
            return "verbs/form";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/verbs";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("form") VerbForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID động từ không hợp lệ.");
            return "redirect:/verbs";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("verbId", id);
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "verbs");
            return "verbs/form";
        }

        try {
            verbService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật động từ thành công!");
            return "redirect:/verbs/" + id;
        } catch (IllegalArgumentException | NoSuchElementException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("verbId", id);
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("currentNav", "verbs");
            return "verbs/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID động từ không hợp lệ.");
            return "redirect:/verbs";
        }
        try {
            verbService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa động từ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa động từ: " + e.getMessage());
        }
        return "redirect:/verbs";
    }

    @GetMapping("/bulk")
    public String bulkForm(Model model) {
        model.addAttribute("bulkForm", new VerbBulkForm());
        model.addAttribute("topics", topicService.findAllOrdered());
        model.addAttribute("currentNav", "verbs");
        return "verbs/bulk";
    }

    @PostMapping("/bulk")
    public String processBulk(@Valid @ModelAttribute("bulkForm") VerbBulkForm bulkForm,
                              BindingResult bindingResult,
                              Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("currentNav", "verbs");
            return "verbs/bulk";
        }

        try {
            List<VerbBulkResultItem> results = verbService.bulkAdd(bulkForm.getText(), bulkForm.getTopicId());
            long successCount = results.stream().filter(VerbBulkResultItem::isSuccess).count();

            model.addAttribute("results", results);
            model.addAttribute("successCount", successCount);
            model.addAttribute("totalCount", results.size());
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("currentNav", "verbs");
            return "verbs/bulk";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("currentNav", "verbs");
            return "verbs/bulk";
        }
    }

    @PostMapping("/sample-data")
    public String loadSampleData(RedirectAttributes redirectAttributes) {
        try {
            verbService.loadSampleData();
            redirectAttributes.addFlashAttribute("successMessage", "Nạp 5 động từ mẫu thành công!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/verbs";
    }

    @GetMapping("/lab")
    public Object lab(@RequestParam(value = "input", required = false) String input,
                      @RequestParam(value = "group", required = false, defaultValue = "AUTO") String group,
                      HttpServletRequest request,
                      Model model) {
        VerbLabResultDto labResult = verbService.processLabInput(input, group);
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok(labResult);
        }
        model.addAttribute("labResult", labResult);
        model.addAttribute("input", input != null ? input.trim() : "");
        model.addAttribute("selectedGroup", group != null ? group.trim().toUpperCase(Locale.ROOT) : "AUTO");
        model.addAttribute("currentNav", "verbs");
        return "verbs/lab";
    }

    @GetMapping("/export-text")
    public ResponseEntity<byte[]> exportText(@RequestParam(value = "form", required = false, defaultValue = "TE") String formStr,
                                             @RequestParam(value = "topicId", required = false) Long topicId,
                                             @RequestParam(value = "group", required = false) Integer group,
                                             HttpServletRequest request) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        if (!exportRateLimiter.tryAcquire(clientIp)) {
            throw new RateLimitExceededException("Bạn đã vượt quá giới hạn tải xuống (tối đa 20 lần mỗi phút). Vui lòng thử lại sau.");
        }

        TargetForm targetForm = TargetForm.fromString(formStr);
        if (!TargetForm.isSpecificForm(targetForm)) {
            targetForm = TargetForm.TE;
        }

        byte[] fileBytes = verbService.exportQuizletText(topicId, group, targetForm);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String filename = "nihon-verbs-" + targetForm.name().toLowerCase(Locale.ROOT) + "-" + timestamp + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(fileBytes);
    }
}
