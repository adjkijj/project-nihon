package com.nihon.controller;

import com.nihon.dto.PracticeChoiceAnswerForm;
import com.nihon.dto.PracticeChoiceOptionDto;
import com.nihon.dto.PracticeChoiceQuestionDto;
import com.nihon.dto.PracticeChoiceResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.PracticeService;
import com.nihon.service.StudyProgressService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Controller phục vụ chế độ Luyện tập trắc nghiệm 4 lựa chọn (Multiple Choice Practice).
 * Hỗ trợ hai hướng câu hỏi: Nhật -> Việt (JA_VI) và Việt -> Nhật (VI_JA).
 * Chấm điểm và thẩm định đáp án 100% tại máy chủ, chống gian lận qua session.
 */
@Controller
@RequestMapping("/practice/choice")
public class PracticeChoiceController {

    public static final String SESSION_CHOICE_VOCAB_ID = "CHOICE_QUESTION_VOCAB_ID";
    public static final String SESSION_CHOICE_OPTION_IDS = "CHOICE_QUESTION_OPTION_IDS";

    private final PracticeService practiceService;
    private final TopicService topicService;
    private final StudyProgressService studyProgressService;

    public PracticeChoiceController(PracticeService practiceService,
                                    TopicService topicService,
                                    StudyProgressService studyProgressService) {
        this.practiceService = practiceService;
        this.topicService = topicService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Màn hình cấu hình và chọn chủ đề cho chế độ trắc nghiệm 4 lựa chọn.
     */
    @GetMapping
    public String index(@RequestParam(value = "topicId", required = false) Long topicId,
                        @RequestParam(value = "direction", required = false) String direction,
                        Model model) {
        String safeDirection = resolveSafeDirection(direction);

        List<Topic> topics = topicService.findAllOrdered();
        Long effectiveTopicId = topicId;

        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicService.findById(topicId).isPresent();
            if (!topicExists) {
                model.addAttribute("errorMessage", "Chủ đề không tồn tại");
                model.addAttribute("topics", Collections.emptyList());
                model.addAttribute("selectedTopicId", topicId);
                model.addAttribute("selectedDirection", safeDirection);
                model.addAttribute("activeNav", "practice");
                model.addAttribute("mode", "choice");
                return "practice/choice-index";
            }
        }

        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("selectedDirection", safeDirection);
        model.addAttribute("activeNav", "practice");
        model.addAttribute("mode", "choice");

        return "practice/choice-index";
    }

    /**
     * Hiển thị một câu hỏi trắc nghiệm 4 lựa chọn ngẫu nhiên.
     */
    @GetMapping("/question")
    public String showQuestion(@RequestParam(value = "topicId", required = false) Long topicId,
                               @RequestParam(value = "direction", required = false) String direction,
                               @RequestParam(value = "excludeId", required = false) Long excludeId,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        String safeDirection = resolveSafeDirection(direction);

        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            List<Topic> topics = topicService.findAllOrdered();
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicService.findById(topicId).isPresent();
            if (!topicExists) {
                redirectAttributes.addFlashAttribute("errorMessage", "Chủ đề không tồn tại");
                return "redirect:/practice/choice";
            }
        }

        Long effectiveExcludeId = excludeId;
        if (excludeId != null && excludeId <= 0) {
            effectiveExcludeId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        }

        Optional<PracticeChoiceQuestionDto> questionOpt = practiceService.getChoiceQuestion(effectiveTopicId, safeDirection, effectiveExcludeId);

        if (questionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chưa đủ dữ liệu để tạo câu hỏi (yêu cầu tối thiểu 4 từ vựng khác biệt).");
            return "redirect:/practice/choice" + (effectiveTopicId != null ? "?topicId=" + effectiveTopicId + "&direction=" + safeDirection : "?direction=" + safeDirection);
        }

        PracticeChoiceQuestionDto question = questionOpt.get();

        // Lưu danh sách ID các lựa chọn hợp lệ vào session để xác thực khi chấm
        List<Long> optionIds = question.getOptions().stream()
                .map(PracticeChoiceOptionDto::getVocabId)
                .toList();

        session.setAttribute(SESSION_CHOICE_VOCAB_ID, question.getVocabId());
        session.setAttribute(SESSION_CHOICE_OPTION_IDS, optionIds);

        PracticeChoiceAnswerForm form = new PracticeChoiceAnswerForm();
        form.setVocabId(question.getVocabId());
        form.setDirection(safeDirection);
        form.setTopicId(effectiveTopicId);

        model.addAttribute("question", question);
        model.addAttribute("form", form);
        model.addAttribute("activeNav", "practice");
        model.addAttribute("mode", "choice");

        return "practice/choice-question";
    }

    /**
     * Nhận câu trả lời và chấm điểm trắc nghiệm phía máy chủ.
     * Chống CSRF qua CsrfInterceptor và kiểm tra lựa chọn thuộc phiên câu hỏi hiện tại.
     */
    @PostMapping("/check")
    public String checkAnswer(@Valid @ModelAttribute("form") PracticeChoiceAnswerForm form,
                              BindingResult bindingResult,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        String safeDirection = form.getSafeDirection();
        Long topicId = form.getTopicId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu câu trả lời không hợp lệ hoặc bạn chưa chọn đáp án.");
            return "redirect:/practice/choice/question" + buildQueryString(topicId, safeDirection);
        }

        Long sessionVocabId = (Long) session.getAttribute(SESSION_CHOICE_VOCAB_ID);
        @SuppressWarnings("unchecked")
        List<Long> sessionOptionIds = (List<Long>) session.getAttribute(SESSION_CHOICE_OPTION_IDS);

        // Kiểm tra câu hỏi có còn hiệu lực trong session hay không
        if (sessionVocabId == null || sessionOptionIds == null || !sessionVocabId.equals(form.getVocabId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Câu hỏi đã hết hạn hoặc bạn đã nộp câu hỏi này rồi. Vui lòng làm câu tiếp theo.");
            return "redirect:/practice/choice" + buildQueryString(topicId, safeDirection);
        }

        // Chặn trường hợp choiceVocabId không nằm trong 4 lựa chọn đã sinh
        if (!sessionOptionIds.contains(form.getChoiceVocabId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phương án lựa chọn không hợp lệ.");
            return "redirect:/practice/choice/question" + buildQueryString(topicId, safeDirection);
        }

        try {
            PracticeChoiceResultDto result = practiceService.checkChoiceAnswer(form, sessionOptionIds);

            // Xóa câu hỏi khỏi session để chống nộp lại nhiều lần
            session.removeAttribute(SESSION_CHOICE_VOCAB_ID);
            session.removeAttribute(SESSION_CHOICE_OPTION_IDS);

            // Ghi nhận tiến độ vào session
            studyProgressService.recordChoiceAttempt(session, result, result.getTopicId());

            model.addAttribute("result", result);
            model.addAttribute("sessionStats", studyProgressService.getSummary(session, null, null));
            model.addAttribute("activeNav", "practice");
            model.addAttribute("mode", "choice");

            return "practice/choice-result";
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/practice/choice" + buildQueryString(topicId, safeDirection);
        }
    }

    private String resolveSafeDirection(String direction) {
        if (direction != null && "VI_JA".equalsIgnoreCase(direction.trim())) {
            return "VI_JA";
        }
        return "JA_VI";
    }

    private String buildQueryString(Long topicId, String direction) {
        StringBuilder sb = new StringBuilder("?");
        if (topicId != null && topicId > 0) {
            sb.append("topicId=").append(topicId).append("&");
        }
        sb.append("direction=").append(direction);
        return sb.toString();
    }
}

