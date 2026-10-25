package com.nihon.controller;

import com.nihon.dto.TopicForm;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public String list(Model model) {
        List<Topic> topics = topicService.findAllOrdered();
        model.addAttribute("topics", topics);
        return "topics/list";
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
            bindingResult.rejectValue("parentId", "error.parent", e.getMessage());
            model.addAttribute("rootTopics", topicService.findRootTopics());
            model.addAttribute("isEdit", false);
            return "topics/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {
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
            bindingResult.rejectValue("parentId", "error.parent", e.getMessage());
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
}
