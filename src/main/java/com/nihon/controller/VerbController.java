package com.nihon.controller;

import com.nihon.dto.verb.VerbBulkForm;
import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.service.TopicService;
import com.nihon.service.VerbService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/verbs")
public class VerbController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE = 100000;
    private static final int MAX_PAGE_SIZE = 100;

    private final VerbService verbService;
    private final TopicService topicService;

    public VerbController(VerbService verbService, TopicService topicService) {
        this.verbService = verbService;
        this.topicService = topicService;
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
                       Model model) {
        int safePage = Math.max(0, Math.min(MAX_PAGE, page));
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").ascending());
        Page<Verb> verbsPage = verbService.search(group, topicId, keyword, pageable);
        List<Topic> topics = topicService.findAllOrdered();

        model.addAttribute("verbs", verbsPage);
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
}
