package com.nihon.controller;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.dto.KanjiForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.KanjiService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Controller
@RequestMapping("/kanji")
public class KanjiController {

    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 100000;
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final KanjiService kanjiService;

    public KanjiController(KanjiService kanjiService) {
        this.kanjiService = kanjiService;
    }

    @InitBinder("form")
    public void initFormBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    /**
     * Trang danh sách Kanji riêng biệt: hiển thị thẻ/lưới Kanji, tìm kiếm đa tiêu chí và lọc theo chủ đề bài học.
     */
    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "topicId", required = false) Long topicId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                       Model model) {
        if (page < 0) {
            page = 0;
        } else if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            size = DEFAULT_PAGE_SIZE;
        }

        String safeKeyword = sanitizeKeyword(keyword);
        Pageable pageable = PageRequest.of(page, size, Sort.by("sourceLine").ascending());
        List<Topic> topics = kanjiService.getKanjiTopics();

        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || kanjiService.existsTopicById(topicId);
            if (!topicExists) {
                model.addAttribute("errorMessage", "Chủ đề không tồn tại");
                Page<KanjiDetailDto> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                model.addAttribute("kanjiPage", emptyPage);
                model.addAttribute("topics", topics);
                model.addAttribute("selectedTopicId", topicId);
                model.addAttribute("keyword", safeKeyword);
                model.addAttribute("totalCount", kanjiService.countTotal());
                model.addAttribute("activeNav", "kanji");
                return "kanji/list";
            }
        }

        Page<KanjiDetailDto> kanjiPage = kanjiService.search(effectiveTopicId, safeKeyword, pageable);

        model.addAttribute("kanjiPage", kanjiPage);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("totalCount", kanjiService.countTotal());
        model.addAttribute("activeNav", "kanji");

        return "kanji/list";
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmed = keyword.strip();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed;
    }

    /**
     * Mở xem chi tiết một ký tự Kanji riêng biệt.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/kanji";
        }
        Optional<KanjiDetailDto> kanjiOpt = kanjiService.findDetailById(id);
        if (kanjiOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy Kanji với mã ID yêu cầu.");
            return "redirect:/kanji";
        }

        KanjiDetailDto kanji = kanjiOpt.get();
        List<Vocabulary> relatedVocabularies = kanjiService.getRelatedVocabulary(kanji.getCharacter(), kanji.getTopicId());

        model.addAttribute("kanji", kanji);
        model.addAttribute("relatedVocabularies", relatedVocabularies);
        model.addAttribute("activeNav", "kanji");

        return "kanji/detail";
    }

    /**
     * Hiển thị form tạo mới Kanji.
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new KanjiForm());
        }
        model.addAttribute("topics", kanjiService.getKanjiTopics());
        model.addAttribute("isEdit", false);
        model.addAttribute("activeNav", "kanji");
        return "kanji/form";
    }

    /**
     * Xử lý tạo mới Kanji.
     */
    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") KanjiForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", kanjiService.getKanjiTopics());
            model.addAttribute("isEdit", false);
            model.addAttribute("activeNav", "kanji");
            return "kanji/form";
        }

        try {
            kanjiService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm Kanji mới thành công!");
            return "redirect:/kanji";
        } catch (IllegalArgumentException e) {
            rejectKanjiBusinessError(bindingResult, e.getMessage());
            model.addAttribute("topics", kanjiService.getKanjiTopics());
            model.addAttribute("isEdit", false);
            model.addAttribute("activeNav", "kanji");
            return "kanji/form";
        }
    }

    /**
     * Hiển thị form chỉnh sửa Kanji.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/kanji";
        }
        return kanjiService.findById(id).map(entry -> {
            if (!model.containsAttribute("form")) {
                model.addAttribute("form", kanjiService.toForm(entry));
            }
            Long tId = entry.getTopic() != null ? entry.getTopic().getId() : null;
            List<Vocabulary> relatedVocabs = kanjiService.getRelatedVocabulary(entry.getCharacter(), tId);
            model.addAttribute("relatedWordCount", relatedVocabs != null ? relatedVocabs.size() : 0);
            model.addAttribute("topics", kanjiService.getKanjiTopics());
            model.addAttribute("isEdit", true);
            model.addAttribute("kanjiId", id);
            model.addAttribute("activeNav", "kanji");
            return "kanji/form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy Kanji với mã ID yêu cầu.");
            return "redirect:/kanji";
        });
    }

    /**
     * Xử lý cập nhật Kanji.
     */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("form") KanjiForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/kanji";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", kanjiService.getKanjiTopics());
            model.addAttribute("isEdit", true);
            model.addAttribute("kanjiId", id);
            model.addAttribute("activeNav", "kanji");
            return "kanji/form";
        }

        try {
            Optional<KanjiEntry> beforeOpt = kanjiService.findById(id);
            String oldCharacter = beforeOpt.map(KanjiEntry::getCharacter).orElse(null);
            Long oldTopicId = beforeOpt.map(k -> k.getTopic() != null ? k.getTopic().getId() : null).orElse(null);
            boolean hadRelatedWords = false;
            if (oldCharacter != null) {
                List<Vocabulary> oldRelated = kanjiService.getRelatedVocabulary(oldCharacter, oldTopicId);
                hadRelatedWords = oldRelated != null && !oldRelated.isEmpty();
            }

            KanjiEntry updated = kanjiService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật Kanji thành công!");
            if (oldCharacter != null && updated != null
                    && !oldCharacter.equals(updated.getCharacter()) && hadRelatedWords) {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Các từ cần nhớ liên kết theo ký tự sẽ không còn khớp.");
            }
            return "redirect:/kanji";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy Kanji để cập nhật.");
            return "redirect:/kanji";
        } catch (IllegalArgumentException e) {
            rejectKanjiBusinessError(bindingResult, e.getMessage());
            model.addAttribute("topics", kanjiService.getKanjiTopics());
            model.addAttribute("isEdit", true);
            model.addAttribute("kanjiId", id);
            model.addAttribute("activeNav", "kanji");
            return "kanji/form";
        }
    }

    /**
     * Xử lý xóa Kanji (không xóa từ vựng liên quan).
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID không hợp lệ");
            return "redirect:/kanji";
        }
        try {
            kanjiService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa Kanji thành công.");
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy Kanji cần xóa (ID không hợp lệ).");
        }
        return "redirect:/kanji";
    }

    private void rejectKanjiBusinessError(BindingResult bindingResult, String message) {
        if (message != null && (message.contains("Kanji này đã tồn tại") || message.contains("ký tự Kanji"))) {
            bindingResult.rejectValue("character", "error.character", message);
        } else {
            bindingResult.rejectValue("topicId", "error.topic", message);
        }
    }
}
