package com.nihon.controller;

import com.nihon.dto.PracticeViJaAnswerForm;
import com.nihon.dto.PracticeViJaQuestionDto;
import com.nihon.dto.PracticeViJaResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.PracticeService;
import com.nihon.service.TopicService;
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

import com.nihon.service.StudyProgressService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Controller
@RequestMapping("/practice/vi-ja")
public class PracticeViJaController {

    private final PracticeService practiceService;
    private final TopicService topicService;
    private final StudyProgressService studyProgressService;

    public PracticeViJaController(PracticeService practiceService,
                                  TopicService topicService,
                                  StudyProgressService studyProgressService) {
        this.practiceService = practiceService;
        this.topicService = topicService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Màn hình chọn chủ đề hoặc bắt đầu kiểm tra Việt → Nhật.
     */
    @GetMapping
    public String index(@RequestParam(value = "topicId", required = false) Long topicId, Model model) {
        List<Topic> topics = topicService.findAllOrdered();
        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("activeNav", "practice");
        model.addAttribute("mode", "vi-ja");
        return "practice/vi-ja-index";
    }

    /**
     * Hiển thị câu hỏi kiểm tra từ tiếng Việt sang tiếng Nhật.
     */
    @GetMapping("/question")
    public String showQuestion(@RequestParam(value = "topicId", required = false) Long topicId,
                               @RequestParam(value = "excludeId", required = false) Long excludeId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        Optional<PracticeViJaQuestionDto> questionOpt = practiceService.getReverseQuestion(topicId, excludeId);

        if (questionOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng nào thuộc chủ đề đã chọn để kiểm tra.");
            return "redirect:/practice/vi-ja";
        }

        PracticeViJaQuestionDto question = questionOpt.get();
        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(question.getVocabId());
        form.setTopicId(topicId);

        model.addAttribute("question", question);
        model.addAttribute("form", form);
        model.addAttribute("activeNav", "practice");
        model.addAttribute("mode", "vi-ja");

        return "practice/vi-ja-question";
    }

    /**
     * Chấm điểm câu trả lời tiếng Nhật tại máy chủ.
     * Chống CSRF thông qua CsrfInterceptor và validate DTO đầu vào.
     */
    @PostMapping("/check")
    public String checkAnswer(@Valid @ModelAttribute("form") PracticeViJaAnswerForm form,
                              BindingResult bindingResult,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu câu trả lời không hợp lệ hoặc vượt quá độ dài quy định.");
            Long topicId = form.getTopicId();
            return "redirect:/practice/vi-ja/question" + (topicId != null ? "?topicId=" + topicId : "");
        }

        try {
            PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);
            studyProgressService.recordViJaAttempt(session, result, form.getTopicId());
            model.addAttribute("result", result);
            model.addAttribute("sessionStats", studyProgressService.getSummary(session, null, null));
            model.addAttribute("activeNav", "practice");
            model.addAttribute("mode", "vi-ja");
            return "practice/vi-ja-result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã từ vựng không tồn tại hoặc đã bị thay đổi.");
            return "redirect:/practice/vi-ja";
        }
    }
}
