package com.nihon.controller;

import com.nihon.dto.exam.result.ExamResultViewDto;
import com.nihon.dto.exam.taker.ExamAnswerResponse;
import com.nihon.dto.exam.taker.ExamAnswerSubmission;
import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.history.LearnerContext;
import com.nihon.service.exam.ExamTakingService;
import com.nihon.service.exam.grading.ExamGradingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controller phục vụ học viên làm bài thi trực tuyến (F42 - F48).
 * Quản lý danh sách đề thi, giới thiệu đề, phòng thi đếm ngược, lưu nháp tự động,
 * nộp bài, xem kết quả chi tiết, tự chấm điểm bài luận và luyện lại các câu sai.
 */
@Controller
@RequestMapping("/exams")
public class ExamTakingController {

    private final ExamTakingService examTakingService;
    private final ExamGradingService gradingService;

    public ExamTakingController(ExamTakingService examTakingService,
                                ExamGradingService gradingService) {
        this.examTakingService = examTakingService;
        this.gradingService = gradingService;
    }

    /**
     * 1. Danh sách đề thi đã xuất bản cho học viên (F42).
     */
    @GetMapping
    public String listExams(@RequestParam(value = "category", required = false) String categoryStr,
                            Model model) {
        ExamCategory category = ExamCategory.isValid(categoryStr) ? ExamCategory.fromString(categoryStr) : null;
        List<ExamPaper> exams = examTakingService.getPublishedExams(category);

        model.addAttribute("exams", exams);
        model.addAttribute("categories", ExamCategory.values());
        model.addAttribute("selectedCategory", category != null ? category.name() : "");
        model.addAttribute("currentNav", "exams");
        return "exams/taker/list";
    }

    /**
     * 2. Giới thiệu đề thi trước khi bắt đầu (thời lượng, số câu, điểm số, các phần thi).
     */
    @GetMapping("/{code}")
    public String examIntro(@PathVariable("code") String code,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            ExamPaper paper = examTakingService.getPublishedExamByCode(code);
            model.addAttribute("paper", paper);
            model.addAttribute("currentNav", "exams");
            return "exams/taker/intro";
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams";
        }
    }

    /**
     * 3. Bắt đầu lượt làm bài mới, khởi tạo ExamSessionAttempt và chuyển hướng vào phòng thi.
     */
    @PostMapping("/{code}/start")
    public String startExam(@PathVariable("code") String code,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            ExamSessionAttempt attempt = examTakingService.startExam(code, session);
            return "redirect:/exams/attempt/" + attempt.getAttemptId();
        } catch (NoSuchElementException | IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams";
        }
    }

    /**
     * 4. Phòng làm bài thi trực tuyến (F42, F46, F47, F48).
     * Chặn quay lại sửa sau khi đã nộp hoặc hết hạn.
     */
    @GetMapping("/attempt/{attemptId}")
    public String examRoom(@PathVariable("attemptId") String attemptId,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        ExamSessionAttempt attempt = examTakingService.getAttempt(attemptId, session);
        if (attempt == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Phiên làm bài thi không tồn tại hoặc bạn không có quyền truy cập.");
            return "redirect:/exams";
        }

        // Chặn quay lại sửa sau khi đã nộp hoặc hết hạn
        if (attempt.isCompleted()) {
            return "redirect:/exams/attempt/" + attemptId + "/result";
        }

        long remainingSeconds = attempt.getRemainingSeconds(Instant.now());

        model.addAttribute("attempt", attempt);
        model.addAttribute("paper", attempt.getPaperDto());
        model.addAttribute("remainingSeconds", remainingSeconds);
        model.addAttribute("currentNav", "exams");
        return "exams/taker/room";
    }

    /**
     * 5. Tự động lưu nháp câu trả lời qua AJAX (F48).
     */
    @PostMapping("/attempt/{attemptId}/answer")
    @ResponseBody
    public ResponseEntity<ExamAnswerResponse> saveAnswer(
            @PathVariable("attemptId") String attemptId,
            @RequestParam("questionId") Long questionId,
            @RequestParam(value = "answer", required = false) String answer,
            @RequestParam(value = "markedForReview", required = false) Boolean markedForReview,
            HttpSession session) {

        if (questionId == null || questionId <= 0) {
            return ResponseEntity.badRequest().body(ExamAnswerResponse.builder()
                    .status("ERROR")
                    .message("Mã câu hỏi không hợp lệ.")
                    .build());
        }

        if (answer != null && answer.length() > 1000) {
            return ResponseEntity.badRequest().body(ExamAnswerResponse.builder()
                    .status("ERROR")
                    .questionId(questionId)
                    .message("Câu trả lời không được vượt quá 1000 ký tự.")
                    .build());
        }

        try {
            ExamAnswerSubmission submission = ExamAnswerSubmission.builder()
                    .questionId(questionId)
                    .answer(answer)
                    .markedForReview(markedForReview)
                    .build();

            ExamAnswerResponse response = examTakingService.saveAnswer(attemptId, submission, session);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ExamAnswerResponse.builder()
                    .status("ERROR")
                    .questionId(questionId)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * 6. Nộp bài thi. Chấm điểm trên máy chủ và chuyển hướng sang trang kết quả.
     */
    @PostMapping("/attempt/{attemptId}/finish")
    public String finishExam(@PathVariable("attemptId") String attemptId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            ExamSessionAttempt attempt = examTakingService.finishExam(attemptId, session);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bạn đã hoàn tất nộp bài thi '" + attempt.getPaperTitle() + "'.");
            return "redirect:/exams/attempt/" + attemptId + "/result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams";
        }
    }

    /**
     * 7. Trang kết quả bài thi chi tiết (F40 - F45).
     * Chỉ người học có đúng learner_id mới xem được. Người khác nhận 404 Not Found.
     */
    @GetMapping("/attempt/{attemptId}/result")
    public String examResult(@PathVariable("attemptId") String attemptId,
                             HttpSession session,
                             Model model) {
        ExamSessionAttempt sessionAttempt = examTakingService.getAttempt(attemptId, session);
        String learnerId = LearnerContext.current().orElse(null);
        if (sessionAttempt != null && sessionAttempt.getLearnerId() != null) {
            learnerId = sessionAttempt.getLearnerId();
        }

        // Nếu bài thi trong session vẫn đang RUNNING, chuyển về phòng thi
        if (sessionAttempt != null && sessionAttempt.isRunning()) {
            return "redirect:/exams/attempt/" + attemptId;
        }

        // Nếu trong session chưa được nộp nhưng đã kết thúc, tiến hành lưu và chấm
        if (sessionAttempt != null && sessionAttempt.isCompleted()) {
            gradingService.gradeAndPersist(sessionAttempt, learnerId != null ? learnerId : sessionAttempt.getLearnerId());
        }

        // Lấy báo cáo chi tiết từ CSDL với kiểm tra bảo mật learnerId
        ExamResultViewDto resultView = gradingService.buildResultView(attemptId, learnerId);

        model.addAttribute("result", resultView);
        model.addAttribute("attempt", resultView.getAttempt());
        model.addAttribute("paper", resultView.getPaper());
        model.addAttribute("currentNav", "exams");
        return "exams/taker/result";
    }

    /**
     * 8. Tự chấm điểm cho câu hỏi tự luận (ESSAY - F45).
     */
    @PostMapping("/attempt/{attemptId}/grade-essay")
    public String gradeEssay(@PathVariable("attemptId") String attemptId,
                             @RequestParam("questionId") Long questionId,
                             @RequestParam("points") BigDecimal points,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        String learnerId = LearnerContext.current().orElse(null);
        ExamSessionAttempt sessionAttempt = examTakingService.getAttempt(attemptId, session);
        if (sessionAttempt != null && sessionAttempt.getLearnerId() != null) {
            learnerId = sessionAttempt.getLearnerId();
        }
        try {
            gradingService.gradeEssay(attemptId, questionId, points, learnerId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật điểm tự chấm cho câu hỏi luận thành công.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/exams/attempt/" + attemptId + "/result";
    }

    /**
     * 9. Luyện lại các câu sai của bài thi (F48).
     */
    @PostMapping("/attempt/{attemptId}/retry-incorrect")
    public String retryIncorrect(@PathVariable("attemptId") String attemptId,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        String learnerId = LearnerContext.current().orElse(null);
        ExamSessionAttempt sessionAttempt = examTakingService.getAttempt(attemptId, session);
        if (sessionAttempt != null && sessionAttempt.getLearnerId() != null) {
            learnerId = sessionAttempt.getLearnerId();
        }
        try {
            ExamSessionAttempt retryAttempt = gradingService.createRetryMiniAttempt(attemptId, learnerId);
            examTakingService.putSessionAttempt(session, retryAttempt);
            redirectAttributes.addFlashAttribute("successMessage", "Đã khởi tạo bài luyện tập lại gồm các câu bạn đã trả lời chưa đúng.");
            return "redirect:/exams/attempt/" + retryAttempt.getAttemptId();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams/attempt/" + attemptId + "/result";
        }
    }
}
