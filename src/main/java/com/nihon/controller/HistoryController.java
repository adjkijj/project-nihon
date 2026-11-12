package com.nihon.controller;

import com.nihon.history.HistoryViews;
import com.nihon.history.LearnerContext;
import com.nihon.service.StudyHistoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/** Lịch sử học tập bền vững. Mã người học chỉ lấy từ cookie (LearnerContext), không từ tham số. */
@Controller
@RequestMapping("/history")
public class HistoryController {

    private final StudyHistoryService historyService;

    public HistoryController(StudyHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public String index(@RequestParam(value = "mode", required = false) String mode,
                        @RequestParam(value = "range", required = false) String range,
                        @RequestParam(value = "page", required = false) String page,
                        Model model) {
        String safeMode = StudyHistoryService.normalizeMode(mode);
        String safeRange = StudyHistoryService.normalizeRange(range);
        Optional<String> learner = LearnerContext.current();

        model.addAttribute("currentNav", "history");
        model.addAttribute("mode", safeMode);
        model.addAttribute("range", safeRange);
        model.addAttribute("modes", StudyHistoryService.MODES.stream().sorted().toList());
        model.addAttribute("ranges", List.of("7D", "30D", "ALL"));
        model.addAttribute("learnerAvailable", learner.isPresent());

        if (learner.isPresent()) {
            String id = learner.get();
            HistoryViews.PageResult result = historyService.findPage(id, safeMode, safeRange,
                    StudyHistoryService.parsePage(page));
            model.addAttribute("sessions", result.sessions());
            model.addAttribute("page", result.page());
            model.addAttribute("totalPages", result.totalPages());
            model.addAttribute("totalElements", result.totalElements());
            model.addAttribute("hasPrev", result.page() > 1);
            model.addAttribute("hasNext", result.page() < result.totalPages());
            model.addAttribute("summary", historyService.summary(id, safeMode, safeRange));
            model.addAttribute("weeklyBars", historyService.weeklyAccuracy(id, safeMode));
        } else {
            model.addAttribute("sessions", List.of());
            model.addAttribute("page", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("totalElements", 0L);
            model.addAttribute("hasPrev", false);
            model.addAttribute("hasNext", false);
            model.addAttribute("summary", new HistoryViews.Summary(0, 0, 0, 0, 0, 0));
            model.addAttribute("weeklyBars", List.of());
        }
        return "history/index";
    }

    @PostMapping("/delete-all")
    public String deleteAll(RedirectAttributes redirect) {
        Optional<String> learner = LearnerContext.current();
        if (learner.isPresent()) {
            int n = historyService.deleteAll(learner.get());
            redirect.addFlashAttribute("historyMessage", "Đã xoá " + n + " phiên học khỏi lịch sử.");
        } else {
            redirect.addFlashAttribute("historyMessage", "Không thể xoá: trình duyệt đang tắt cookie.");
        }
        return "redirect:/history";
    }

    /** Lưu tổng hợp các bản ghi trong phiên hiện tại vào lịch sử rồi xoá chúng. Chỉ khi có bản ghi. */
    @PostMapping("/end-session")
    public String endSession(HttpSession session, RedirectAttributes redirect) {
        Optional<String> learner = LearnerContext.current();
        if (learner.isPresent()) {
            int saved = historyService.endSession(learner.get(), session);
            redirect.addFlashAttribute("historyMessage", saved > 0
                    ? "Đã lưu " + saved + " phiên học vào lịch sử."
                    : "Không có kết quả mới để lưu.");
        } else {
            redirect.addFlashAttribute("historyMessage", "Không thể lưu lịch sử: trình duyệt đang tắt cookie.");
        }
        return "redirect:/history";
    }
}