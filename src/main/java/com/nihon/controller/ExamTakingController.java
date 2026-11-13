package com.nihon.controller;

import com.nihon.dto.exam.taker.ExamAnswerResponse;
import com.nihon.dto.exam.taker.ExamAnswerSubmission;
import com.nihon.dto.exam.taker.ExamAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.service.exam.ExamTakingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Controller phục vụ học viên làm bài thi trực tuyến (F42 - F48).
 * Quản lý danh sách đề thi, giới thiệu đề, phòng thi đếm ngược, lưu nháp tự động và nộp bài.
 */
@Controller
@RequestMapping("/exams")
public class ExamTakingController {

    private final ExamTakingService examTakingService;

    public ExamTakingController(ExamTakingService examTakingService) {
        this.examTakingService = examTakingService;
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
     * 3. Bắt đầu lượt làm bài mới, khởi tạo ExamAttempt và chuyển hướng vào phòng thi.
     */
    @PostMapping("/{code}/start")
    public String startExam(@PathVariable("code") String code,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            ExamAttempt attempt = examTakingService.startExam(code, session);
            return "redirect:/exams/attempt/" + attempt.getAttemptId();
        } catch (NoSuchElementException | IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams";
        }
    }

    /**
     * 4. Phòng làm bài thi trực tuyến (F42, F46, F47, F48).
     * Chặn quay lại sửa sau khi đã nộp.
     */
    @GetMapping("/attempt/{attemptId}")
    public String examRoom(@PathVariable("attemptId") String attemptId,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        ExamAttempt attempt = examTakingService.getAttempt(attemptId, session);
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
     * 6. Nộp bài thi. Ngăn nộp lần hai.
     */
    @PostMapping("/attempt/{attemptId}/finish")
    public String finishExam(@PathVariable("attemptId") String attemptId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            ExamAttempt attempt = examTakingService.finishExam(attemptId, session);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bạn đã hoàn tất nộp bài thi '" + attempt.getPaperTitle() + "'.");
            return "redirect:/exams/attempt/" + attemptId + "/result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exams";
        }
    }

    /**
     * 7. Màn hình tóm tắt kết quả nộp bài thi (Prompt 20 tóm tắt, Prompt 21 kết quả chi tiết).
     */
    @GetMapping("/attempt/{attemptId}/result")
    public String examResult(@PathVariable("attemptId") String attemptId,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        ExamAttempt attempt = examTakingService.getAttempt(attemptId, session);
        if (attempt == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phiên làm bài thi không tồn tại.");
            return "redirect:/exams";
        }

        // Nếu bài thi vẫn đang chạy thì quay lại phòng thi
        if (attempt.isRunning()) {
            return "redirect:/exams/attempt/" + attemptId;
        }

        model.addAttribute("attempt", attempt);
        model.addAttribute("paper", attempt.getPaperDto());
        model.addAttribute("currentNav", "exams");
        return "exams/taker/result";
    }
}
