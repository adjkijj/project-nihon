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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

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
    public String index(@RequestParam(value = "topicId", required = false) Long topicId,
                        HttpSession session,
                        Model model) {
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
                model.addAttribute("wrongCount", 0);
                model.addAttribute("activeNav", "practice");
                model.addAttribute("mode", "vi-ja");
                return "practice/vi-ja-index";
            }
        }

        int wrongCount = studyProgressService.getWrongVocabIds(session, "VI_JA", effectiveTopicId).size();

        model.addAttribute("topics", topics);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("wrongCount", wrongCount);
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
                               @RequestParam(value = "retryWrongOnly", required = false, defaultValue = "false") String retryWrongOnlyParam,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        boolean retryWrongOnly = parseRetryWrongOnly(retryWrongOnlyParam);

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
                return "redirect:/practice/vi-ja";
            }
        }

        Long effectiveExcludeId = excludeId;
        if (excludeId != null && excludeId <= 0) {
            effectiveExcludeId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        }

        Set<Long> allowedIds = null;
        if (retryWrongOnly) {
            Set<Long> wrongIds = studyProgressService.getWrongVocabIds(session, "VI_JA", effectiveTopicId);
            if (wrongIds == null || wrongIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa có từ nào làm sai trong phiên này.");
                return "redirect:/practice/vi-ja" + (effectiveTopicId != null ? "?topicId=" + effectiveTopicId : "");
            }
            allowedIds = wrongIds;
        }

        Optional<PracticeViJaQuestionDto> questionOpt;
        if (allowedIds != null) {
            questionOpt = practiceService.getReverseQuestion(effectiveTopicId, effectiveExcludeId, allowedIds);
        } else {
            questionOpt = practiceService.getReverseQuestion(effectiveTopicId, effectiveExcludeId);
        }

        if (questionOpt.isEmpty()) {
            if (retryWrongOnly) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa có từ nào làm sai trong phiên này.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy từ vựng nào thuộc chủ đề đã chọn để kiểm tra.");
            }
            return "redirect:/practice/vi-ja" + (effectiveTopicId != null ? "?topicId=" + effectiveTopicId : "");
        }

        PracticeViJaQuestionDto question = questionOpt.get();
        PracticeViJaAnswerForm form = new PracticeViJaAnswerForm();
        form.setVocabId(question.getVocabId());
        form.setTopicId(effectiveTopicId);
        form.setRetryWrongOnly(retryWrongOnly);

        model.addAttribute("question", question);
        model.addAttribute("form", form);
        model.addAttribute("retryWrongOnly", retryWrongOnly);
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
                              @RequestParam(value = "retryWrongOnly", required = false, defaultValue = "false") String retryWrongOnlyParam,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        boolean retryWrongOnly = (form != null && form.isRetryWrongOnly()) || parseRetryWrongOnly(retryWrongOnlyParam);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu câu trả lời không hợp lệ hoặc vượt quá độ dài quy định.");
            Long topicId = form != null ? form.getTopicId() : null;
            StringBuilder redirectUrl = new StringBuilder("redirect:/practice/vi-ja/question");
            boolean hasParam = false;
            if (topicId != null && topicId > 0) {
                redirectUrl.append("?topicId=").append(topicId);
                hasParam = true;
            }
            if (retryWrongOnly) {
                redirectUrl.append(hasParam ? "&" : "?").append("retryWrongOnly=true");
            }
            return redirectUrl.toString();
        }

        try {
            PracticeViJaResultDto result = practiceService.checkReverseAnswer(form);
            studyProgressService.recordViJaAttempt(session, result, result.getTopicId());

            int wrongCount = studyProgressService.getWrongVocabIds(session, "VI_JA", result.getTopicId()).size();
            boolean retryCompleted = retryWrongOnly && wrongCount == 0;

            model.addAttribute("result", result);
            model.addAttribute("retryWrongOnly", retryWrongOnly);
            model.addAttribute("retryCompleted", retryCompleted);
            model.addAttribute("wrongCount", wrongCount);
            model.addAttribute("sessionStats", studyProgressService.getSummary(session, null, null));
            model.addAttribute("activeNav", "practice");
            model.addAttribute("mode", "vi-ja");
            return "practice/vi-ja-result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mã từ vựng không tồn tại hoặc đã bị thay đổi.");
            return "redirect:/practice/vi-ja";
        }
    }

    private boolean parseRetryWrongOnly(String val) {
        if (val == null || val.isBlank()) {
            return false;
        }
        String s = val.strip().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }
}
