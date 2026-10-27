package com.nihon.controller;

import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/vocabulary")
public class VocabularyController {

    private final VocabularyService vocabularyService;
    private final TopicService topicService;

    public VocabularyController(VocabularyService vocabularyService, TopicService topicService) {
        this.vocabularyService = vocabularyService;
        this.topicService = topicService;
    }

    @GetMapping
    public String list(@RequestParam(value = "topicId", required = false) Long topicId,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       Model model) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").descending());

        Page<VocabularyRowDto> vocabPage = vocabularyService.search(topicId, keyword, pageable);
        List<Topic> topics = topicService.findAllOrdered();

        model.addAttribute("vocabPage", vocabPage);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("keyword", keyword);

        return "vocabulary/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new VocabularyForm());
        }
        model.addAttribute("topics", topicService.findAllOrdered());
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
            vocabularyService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm từ vựng mới thành công!");
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
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return vocabularyService.findById(id).map(v -> {
            if (!model.containsAttribute("form")) {
                model.addAttribute("form", vocabularyService.toForm(v));
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
        if (bindingResult.hasErrors()) {
            model.addAttribute("topics", topicService.findAllOrdered());
            model.addAttribute("isEdit", true);
            model.addAttribute("vocabId", id);
            return "vocabulary/form";
        }

        try {
            vocabularyService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật từ vựng thành công!");
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
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            vocabularyService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa từ vựng thành công.");
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng cần xóa (ID không hợp lệ).");
        }
        return "redirect:/vocabulary";
    }
}
