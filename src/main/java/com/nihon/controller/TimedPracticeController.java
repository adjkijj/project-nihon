package com.nihon.controller;

import com.nihon.dto.timed.TimedAnswerForm;
import com.nihon.dto.timed.TimedEvaluationMode;
import com.nihon.dto.timed.TimedFinishForm;
import com.nihon.dto.timed.TimedPracticeType;
import com.nihon.dto.timed.TimedQuestionDto;
import com.nihon.dto.timed.TimedResultDto;
import com.nihon.dto.timed.TimedSession;
import com.nihon.dto.timed.TimedSessionStatus;
import com.nihon.dto.timed.TimedStartForm;
import com.nihon.entity.Topic;
import com.nihon.service.TimedPracticeService;
import com.nihon.service.TopicService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controller quản lý chế độ Luyện tập tính giờ (F42, F50, F17).
 * Hỗ trợ giao diện thiết lập, phòng thi đếm ngược, nộp bài tự động/thủ công và tổng kết điểm.
 */
@Controller
@RequestMapping("/practice/timed")
public class TimedPracticeController {

    private final TimedPracticeService timedPracticeService;
    private final TopicService topicService;

    public TimedPracticeController(TimedPracticeService timedPracticeService,
                                   TopicService topicService) {
        this.timedPracticeService = timedPracticeService;
        this.topicService = topicService;
    }

    /**
     * Màn hình cấu hình bài thi tính giờ.
     * Nếu đang có phiên RUNNING, truyền activeSession ra view để người dùng chọn tiếp tục hoặc hủy.
     */
    @GetMapping
    public String index(HttpSession session, Model model) {
        TimedSession activeSession = timedPracticeService.getActiveSession(session);
        if (activeSession != null && activeSession.isRunning()) {
            model.addAttribute("activeSession", activeSession);
        }

        List<Topic> topics = topicService.findAllOrdered();
        model.addAttribute("topics", topics);
        model.addAttribute("activeNav", "practice");

        if (!model.containsAttribute("startForm")) {
            model.addAttribute("startForm", TimedStartForm.builder()
                    .questionCount(10)
                    .durationMinutes(5)
                    .type(TimedPracticeType.JA_VI)
                    .evaluationMode(TimedEvaluationMode.INSTANT)
                    .build());
        }

        return "practice/timed-index";
    }

    /**
     * Khởi tạo bài thi tính giờ mới.
     * Nếu có bài cũ đang chạy mà chưa xác nhận hủy (confirmCancelOld != true), cảnh báo người học.
     */
    @PostMapping("/start")
    public String start(@Valid @ModelAttribute("startForm") TimedStartForm form,
                        BindingResult bindingResult,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", firstError != null ? firstError : "Cấu hình không hợp lệ.");
            return "redirect:/practice/timed";
        }

        TimedSession active = timedPracticeService.getActiveSession(session);
        if (active != null && active.isRunning()) {
            if (!Boolean.TRUE.equals(form.getConfirmCancelOld())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Đang có bài thi tính giờ đang diễn ra. Bạn cần xác nhận hủy bài thi cũ trước khi bắt đầu bài mới.");
                return "redirect:/practice/timed";
            }
            timedPracticeService.cancelSession(session);
        }

        try {
            timedPracticeService.startSession(form, session);
            return "redirect:/practice/timed/run";
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/practice/timed";
        }
    }

    /**
     * Màn hình câu hỏi đang làm bài trong phòng thi đếm ngược.
     * Kiểm tra thời gian: nếu đã quá hạn, hoàn tất bài thi và chuyển hướng đến trang kết quả.
     */
    @GetMapping("/run")
    public String run(HttpSession session, Model model) {
        TimedQuestionDto question = timedPracticeService.getCurrentQuestion(session);
        if (question == null) {
            return "redirect:/practice/timed/result";
        }

        model.addAttribute("question", question);
        model.addAttribute("remainingSeconds", question.getRemainingSeconds());
        model.addAttribute("activeNav", "practice");

        if (!model.containsAttribute("answerForm")) {
            model.addAttribute("answerForm", TimedAnswerForm.builder()
                    .sessionId(question.getSessionId())
                    .questionIndex(question.getQuestionIndex())
                    .build());
        }

        return "practice/timed-run";
    }

    /**
     * Nộp câu trả lời cho câu hỏi hiện tại.
     * Chấm điểm và chuyển tiếp sang câu tiếp theo, hoặc chuyển hướng đến kết quả nếu đã hết câu/hết giờ.
     */
    @PostMapping("/answer")
    public String answer(@Valid @ModelAttribute("answerForm") TimedAnswerForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg != null ? errorMsg : "Dữ liệu trả lời không hợp lệ.");
            return "redirect:/practice/timed/run";
        }

        try {
            TimedSession updated = timedPracticeService.answerQuestion(form, session);
            if (updated.getStatus() != TimedSessionStatus.RUNNING || updated.getCurrentIndex() >= updated.getQuestions().size()) {
                return "redirect:/practice/timed/result";
            }
            return "redirect:/practice/timed/run";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/practice/timed/run";
        }
    }

    /**
     * Nộp bài sớm hoặc nộp tự động khi hết giờ từ máy khách.
     */
    @PostMapping("/finish")
    public String finish(@ModelAttribute TimedFinishForm form, HttpSession session) {
        Object attr = session.getAttribute(TimedPracticeService.SESSION_TIMED_PRACTICE);
        if (attr instanceof TimedSession s && s.isRunning()) {
            Instant now = Instant.now();
            TimedSessionStatus finalStatus = s.isExpired(now)
                    ? TimedSessionStatus.EXPIRED
                    : TimedSessionStatus.FINISHED;
            timedPracticeService.finalizeSession(s, session, finalStatus);
        }
        return "redirect:/practice/timed/result";
    }

    /**
     * Màn hình tổng kết kết quả thi.
     */
    @GetMapping("/result")
    public String result(HttpSession session, Model model) {
        TimedResultDto result = timedPracticeService.getResult(session);
        if (result == null) {
            return "redirect:/practice/timed";
        }

        model.addAttribute("result", result);
        model.addAttribute("activeNav", "practice");
        return "practice/timed-result";
    }

    /**
     * Hủy bỏ bài thi đang làm.
     */
    @PostMapping("/cancel")
    public String cancel(HttpSession session, RedirectAttributes redirectAttributes) {
        timedPracticeService.cancelSession(session);
        redirectAttributes.addFlashAttribute("successMessage", "Đã hủy bài thi tính giờ.");
        return "redirect:/practice/timed";
    }
}
