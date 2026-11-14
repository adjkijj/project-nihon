package com.nihon.controller;

import com.nihon.dto.TopicDetailDto;
import com.nihon.dto.TopicForm;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/topics")
public class TopicController {

    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 100000;

    private final TopicService topicService;
    private final com.nihon.service.VerbService verbService;
    private final com.nihon.service.TopicTestService topicTestService;
    private final com.nihon.repository.GrammarTopicRepository grammarTopicRepository;

    public TopicController(TopicService topicService,
                           com.nihon.service.VerbService verbService,
                           com.nihon.service.TopicTestService topicTestService,
                           com.nihon.repository.GrammarTopicRepository grammarTopicRepository) {
        this.topicService = topicService;
        this.verbService = verbService;
        this.topicTestService = topicTestService;
        this.grammarTopicRepository = grammarTopicRepository;
    }

    @InitBinder("form")
    public void initFormBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "type", required = false) String type,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                       Model model) {
        int safePage = Math.max(0, Math.min(MAX_PAGE, page));
        int safeSize = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;
        String safeKeyword = sanitizeKeyword(keyword);
        String safeType = normalizeType(type);

        List<Topic> allTopics = topicService.findAllOrdered();
        if (allTopics == null) {
            allTopics = Collections.emptyList();
        }

        // Tính danh sách ID có thể di chuyển UP/DOWN trong cùng cấp (cùng parent)
        Set<Long> canMoveUpIds = new HashSet<>();
        Set<Long> canMoveDownIds = new HashSet<>();
        Map<Long, List<Topic>> byParent = new LinkedHashMap<>();
        for (Topic t : allTopics) {
            if (t != null && t.getId() != null) {
                Long parentKey = (t.getParent() != null && t.getParent().getId() != null) ? t.getParent().getId() : 0L;
                byParent.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(t);
            }
        }
        for (List<Topic> group : byParent.values()) {
            for (int i = 0; i < group.size(); i++) {
                Long tid = group.get(i).getId();
                if (i > 0) {
                    canMoveUpIds.add(tid);
                }
                if (i < group.size() - 1) {
                    canMoveDownIds.add(tid);
                }
            }
        }

        // Lọc theo từ khóa tên chủ đề và loại chủ đề
        String lowerKeyword = (safeKeyword != null) ? safeKeyword.toLowerCase(Locale.ROOT) : null;
        List<Topic> filtered = allTopics.stream()
                .filter(t -> {
                    if (t == null) {
                        return false;
                    }
                    if ("KANJI".equals(safeType) && !t.isKanjiSection()) {
                        return false;
                    }
                    if ("VOCAB".equals(safeType) && t.isKanjiSection()) {
                        return false;
                    }
                    if (lowerKeyword != null) {
                        return t.getName() != null && t.getName().toLowerCase(Locale.ROOT).contains(lowerKeyword);
                    }
                    return true;
                })
                .toList();

        int total = filtered.size();
        long fromIndexLong = (long) safePage * (long) safeSize;
        List<Topic> pageContent;
        if (fromIndexLong >= total || fromIndexLong < 0) {
            pageContent = Collections.emptyList();
        } else {
            int fromIndex = (int) fromIndexLong;
            int toIndex = (int) Math.min(fromIndexLong + safeSize, total);
            pageContent = filtered.subList(fromIndex, toIndex);
        }

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Topic> topicPage = new PageImpl<>(pageContent, pageable, total);

        Map<Long, String> cannotDeleteReasons = topicService.getCannotDeleteReasons(pageContent);
        if (cannotDeleteReasons == null) {
            cannotDeleteReasons = Collections.emptyMap();
        }

        model.addAttribute("topics", pageContent);
        model.addAttribute("topicPage", topicPage);
        model.addAttribute("allTopics", allTopics);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("selectedType", safeType);
        model.addAttribute("canMoveUpIds", canMoveUpIds);
        model.addAttribute("canMoveDownIds", canMoveDownIds);
        model.addAttribute("cannotDeleteReasons", cannotDeleteReasons);
        return "topics/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        Optional<TopicDetailDto> detailOpt = topicService.findDetailById(id);
        if (detailOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy chủ đề yêu cầu (ID không hợp lệ).");
            return "redirect:/topics";
        }

        TopicDetailDto detail = detailOpt.get();
        List<Topic> candidateTargets = topicService.findMoveContentTargets(id);
        if (candidateTargets == null) {
            candidateTargets = Collections.emptyList();
        }

        List<com.nihon.dto.verb.VerbDetailDto> topicVerbs = verbService.findByTopicIdWithForms(id);
        detail.setVerbCount(topicVerbs.size());
        detail.setVerbs(topicVerbs);

        model.addAttribute("topic", detail);
        model.addAttribute("children", detail.getChildren());
        model.addAttribute("vocabularyCount", detail.getVocabularyCount());
        model.addAttribute("kanjiCount", detail.getKanjiCount());
        model.addAttribute("verbCount", topicVerbs.size());
        model.addAttribute("verbs", topicVerbs);
        long grammarCount = grammarTopicRepository.countByTopicId(id);
        model.addAttribute("hasGrammarTopics", grammarCount > 0);
        model.addAttribute("grammarCount", grammarCount);
        model.addAttribute("candidateTargetTopics", candidateTargets);
        return "topics/detail";
    }

    @PostMapping("/{id}/move")
    public String move(@PathVariable("id") Long id,
                       @RequestParam(value = "direction", required = false) String direction,
                       RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        try {
            topicService.move(id, direction);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thứ tự chủ đề.");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/topics";
    }

    @PostMapping("/{id}/move-content")
    public String moveContent(@PathVariable("id") Long id,
                              @RequestParam(value = "targetTopicId", required = false) Long targetTopicId,
                              RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0 || targetTopicId == null || targetTopicId <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        try {
            TopicService.TopicMoveContentResult result = topicService.moveContent(id, targetTopicId);
            if (result != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Đã chuyển %d từ vựng, %d Kanji và gộp %d bản ghi từ vựng trùng sang chủ đề đích.",
                                result.movedVocabCount(), result.movedKanjiCount(), result.mergedVocabCount()));
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã chuyển nội dung sang chủ đề đích thành công.");
            }
        } catch (IllegalArgumentException | NoSuchElementException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/topics";
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

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "ALL";
        }
        String upper = type.strip().toUpperCase(Locale.ROOT);
        if ("KANJI".equals(upper) || "VOCAB".equals(upper)) {
            return upper;
        }
        return "ALL";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TopicForm());
        }
        model.addAttribute("rootTopics", topicService.findRootTopics());
        model.addAttribute("isEdit", false);
        return "topics/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") TopicForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("rootTopics", topicService.findRootTopics());
            model.addAttribute("isEdit", false);
            return "topics/form";
        }

        try {
            topicService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo chủ đề mới thành công!");
            return "redirect:/topics";
        } catch (IllegalArgumentException e) {
            String field = (e.getMessage() != null && e.getMessage().contains("Tên chủ đề")) ? "name" : "parentId";
            bindingResult.rejectValue(field, "error." + field, e.getMessage());
            model.addAttribute("rootTopics", topicService.findRootTopics());
            model.addAttribute("isEdit", false);
            return "topics/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        return topicService.findById(id).map(t -> {
            if (!model.containsAttribute("form")) {
                model.addAttribute("form", topicService.toForm(t));
            }
            // Loại bỏ chính chủ đề đang sửa khỏi danh sách chủ đề cha để tránh vòng lặp
            List<Topic> candidateParents = topicService.findRootTopics().stream()
                    .filter(rt -> !rt.getId().equals(id))
                    .toList();
            model.addAttribute("rootTopics", candidateParents);
            model.addAttribute("isEdit", true);
            model.addAttribute("topicId", id);
            return "topics/form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy chủ đề yêu cầu (ID không hợp lệ).");
            return "redirect:/topics";
        });
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("form") TopicForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        if (bindingResult.hasErrors()) {
            List<Topic> candidateParents = topicService.findRootTopics().stream()
                    .filter(rt -> !rt.getId().equals(id))
                    .toList();
            model.addAttribute("rootTopics", candidateParents);
            model.addAttribute("isEdit", true);
            model.addAttribute("topicId", id);
            return "topics/form";
        }

        try {
            topicService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật chủ đề thành công!");
            return "redirect:/topics";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy chủ đề để cập nhật.");
            return "redirect:/topics";
        } catch (IllegalArgumentException e) {
            String field = (e.getMessage() != null && e.getMessage().contains("Tên chủ đề")) ? "name" : "parentId";
            bindingResult.rejectValue(field, "error." + field, e.getMessage());
            List<Topic> candidateParents = topicService.findRootTopics().stream()
                    .filter(rt -> !rt.getId().equals(id))
                    .toList();
            model.addAttribute("rootTopics", candidateParents);
            model.addAttribute("isEdit", true);
            model.addAttribute("topicId", id);
            return "topics/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/topics";
        }
        try {
            topicService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa chủ đề thành công.");
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy chủ đề cần xóa (ID không hợp lệ).");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/topics";
    }

    /**
     * Màn hình kiểm tra cuối bài theo chủ đề (F40 - mode TOPIC_TEST).
     */
    @GetMapping("/{id}/test")
    public String topicTest(@PathVariable("id") Long id,
                            @RequestParam(value = "restart", defaultValue = "false") boolean restart,
                            jakarta.servlet.http.HttpSession session,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID chủ đề không hợp lệ.");
            return "redirect:/topics";
        }

        try {
            com.nihon.service.TopicTestService.TopicTestSession testSession =
                    (com.nihon.service.TopicTestService.TopicTestSession) session.getAttribute(com.nihon.service.TopicTestService.SESSION_ATTR_TOPIC_TEST);

            if (testSession == null || !id.equals(testSession.getTopicId()) || restart) {
                testSession = topicTestService.createTest(id);
                session.setAttribute(com.nihon.service.TopicTestService.SESSION_ATTR_TOPIC_TEST, testSession);
            }

            model.addAttribute("testSession", testSession);
            model.addAttribute("topicId", id);
            model.addAttribute("currentNav", "topics");
            return "topics/test";
        } catch (NoSuchElementException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/topics/" + id;
        }
    }

    /**
     * Nộp bài kiểm tra cuối bài, chấm điểm và lưu vào CSDL (StudySession, mode = TOPIC_TEST).
     */
    @PostMapping("/{id}/test/submit")
    public String submitTopicTest(@PathVariable("id") Long id,
                                  @RequestParam Map<String, String> allParams,
                                  jakarta.servlet.http.HttpSession session,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        com.nihon.service.TopicTestService.TopicTestSession testSession =
                (com.nihon.service.TopicTestService.TopicTestSession) session.getAttribute(com.nihon.service.TopicTestService.SESSION_ATTR_TOPIC_TEST);

        if (testSession == null || !id.equals(testSession.getTopicId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phiên làm bài kiểm tra không tồn tại hoặc đã hết hạn.");
            return "redirect:/topics/" + id;
        }

        // Đọc các đáp án người dùng đã chọn từ form (question_1, question_2, ...)
        Map<Integer, Integer> userAnswers = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("question_")) {
                try {
                    int qIdx = Integer.parseInt(entry.getKey().substring("question_".length()));
                    int choiceIdx = Integer.parseInt(entry.getValue());
                    userAnswers.put(qIdx, choiceIdx);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        String learnerId = com.nihon.history.LearnerContext.current().orElse(null);
        topicTestService.submitTest(testSession, userAnswers, learnerId);

        model.addAttribute("testSession", testSession);
        model.addAttribute("topicId", id);
        model.addAttribute("currentNav", "topics");
        return "topics/test";
    }
}
