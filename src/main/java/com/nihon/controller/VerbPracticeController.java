package com.nihon.controller;

import com.nihon.conjugation.TargetForm;
import com.nihon.dto.verb.PracticeVerbAnswerForm;
import com.nihon.dto.verb.PracticeVerbQuestionDto;
import com.nihon.dto.verb.PracticeVerbResultDto;
import com.nihon.entity.Topic;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import com.nihon.service.VerbPracticeService;
import com.nihon.service.VerbService;
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
 * Controller phục vụ chế độ Luyện chia thể động từ tiếng Nhật (F13 - F18).
 * Hỗ trợ chọn thể đích, nhóm, chủ đề, tự luận (gõ kana/kanji) hoặc trắc nghiệm 4 phương án.
 * Chấm điểm trực tiếp tại máy chủ, chống rò rỉ đáp án trên mã HTML.
 */
@Controller
@RequestMapping("/verbs/practice")
public class VerbPracticeController {

    private final VerbPracticeService verbPracticeService;
    private final VerbService verbService;
    private final TopicService topicService;
    private final StudyProgressService studyProgressService;

    public VerbPracticeController(VerbPracticeService verbPracticeService,
                                  VerbService verbService,
                                  TopicService topicService,
                                  StudyProgressService studyProgressService) {
        this.verbPracticeService = verbPracticeService;
        this.verbService = verbService;
        this.topicService = topicService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Màn hình cấu hình luyện tập chia thể động từ.
     */
    @GetMapping
    public String index(@RequestParam(value = "targetForm", required = false, defaultValue = "TE") String targetFormStr,
                        @RequestParam(value = "group", required = false) Integer group,
                        @RequestParam(value = "topicId", required = false) Long topicId,
                        @RequestParam(value = "mode", required = false, defaultValue = "INPUT") String modeStr,
                        HttpSession session,
                        Model model) {
        TargetForm targetForm = TargetForm.fromString(targetFormStr);
        String mode = "CHOICE".equalsIgnoreCase(modeStr) ? "CHOICE" : "INPUT";

        Long effectiveTopicId = (topicId != null && topicId > 0) ? topicId : null;
        Integer effectiveGroup = (group != null && group >= 1 && group <= 3) ? group : null;

        List<Topic> topics = topicService.findAllOrdered();
        if (topics == null) {
            topics = Collections.emptyList();
        }

        String filterMode = (targetForm != TargetForm.RANDOM) ? "VERB_" + targetForm.name() : "ALL";
        int wrongCount = studyProgressService.getWrongVerbIds(session, filterMode, effectiveTopicId).size();
        int totalWrongAllForms = studyProgressService.getWrongVerbIds(session, "ALL", effectiveTopicId).size();

        model.addAttribute("topics", topics);
        model.addAttribute("selectedForm", targetForm.name());
        model.addAttribute("selectedGroup", effectiveGroup);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("selectedMode", mode);
        model.addAttribute("wrongCount", wrongCount);
        model.addAttribute("totalWrongAllForms", totalWrongAllForms);
        model.addAttribute("currentNav", "verbs");

        return "verbs/practice-index";
    }

    /**
     * Hiển thị một câu hỏi luyện chia thể ngẫu nhiên.
     */
    @GetMapping("/question")
    public String question(@RequestParam(value = "targetForm", required = false, defaultValue = "TE") String targetFormStr,
                           @RequestParam(value = "group", required = false) Integer group,
                           @RequestParam(value = "topicId", required = false) Long topicId,
                           @RequestParam(value = "mode", required = false, defaultValue = "INPUT") String modeStr,
                           @RequestParam(value = "excludeId", required = false) Long excludeId,
                           @RequestParam(value = "retryWrongOnly", required = false, defaultValue = "false") String retryWrongOnlyParam,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        TargetForm targetForm = TargetForm.fromString(targetFormStr);
        String mode = "CHOICE".equalsIgnoreCase(modeStr) ? "CHOICE" : "INPUT";
        boolean retryWrongOnly = Boolean.parseBoolean(retryWrongOnlyParam) || "1".equals(retryWrongOnlyParam);

        Long effectiveTopicId = (topicId != null && topicId > 0) ? topicId : null;
        Integer effectiveGroup = (group != null && group >= 1 && group <= 3) ? group : null;
        Long effectiveExcludeId = (excludeId != null && excludeId > 0) ? excludeId : null;

        Optional<PracticeVerbQuestionDto> questionOpt = verbPracticeService.getNextQuestion(
                targetForm,
                effectiveGroup,
                effectiveTopicId,
                mode,
                effectiveExcludeId,
                retryWrongOnly,
                session
        );

        if (questionOpt.isEmpty()) {
            if (retryWrongOnly) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa có động từ nào làm sai trong phiên này.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Chưa có đủ dữ liệu động từ phù hợp để tạo câu hỏi cho cấu hình đã chọn.");
            }
            return "redirect:/verbs/practice" + buildQueryString(targetForm.name(), effectiveGroup, effectiveTopicId, mode);
        }

        PracticeVerbQuestionDto question = questionOpt.get();

        PracticeVerbAnswerForm form = PracticeVerbAnswerForm.builder()
                .verbId(question.getVerbId())
                .targetForm(question.getTargetForm().name())
                .mode(mode)
                .group(effectiveGroup)
                .topicId(effectiveTopicId)
                .retryWrongOnly(retryWrongOnly)
                .build();

        model.addAttribute("question", question);
        model.addAttribute("form", form);
        model.addAttribute("retryWrongOnly", retryWrongOnly);
        model.addAttribute("currentNav", "verbs");

        return "verbs/practice-question";
    }

    /**
     * Nhận câu trả lời và chấm điểm trực tiếp tại máy chủ.
     */
    @PostMapping("/check")
    public String check(@Valid @ModelAttribute("form") PracticeVerbAnswerForm form,
                        BindingResult bindingResult,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        String targetFormStr = form != null && form.getTargetForm() != null ? form.getTargetForm() : "TE";
        String modeStr = form != null && form.getMode() != null ? form.getMode() : "INPUT";
        Long topicId = form != null ? form.getTopicId() : null;
        Integer group = form != null ? form.getGroup() : null;
        boolean retryWrongOnly = form != null && form.isRetryWrongOnly();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu câu trả lời không hợp lệ hoặc vượt quá độ dài quy định.");
            return "redirect:/verbs/practice/question" + buildQueryString(targetFormStr, group, topicId, modeStr);
        }

        try {
            PracticeVerbResultDto result = verbPracticeService.checkAnswer(form, session);
            model.addAttribute("result", result);
            model.addAttribute("form", form);
            model.addAttribute("currentNav", "verbs");
            return "verbs/practice-result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/verbs/practice" + buildQueryString(targetFormStr, group, topicId, modeStr);
        }
    }

    private String buildQueryString(String targetForm, Integer group, Long topicId, String mode) {
        StringBuilder sb = new StringBuilder("?");
        if (targetForm != null) {
            sb.append("targetForm=").append(targetForm).append("&");
        }
        if (group != null) {
            sb.append("group=").append(group).append("&");
        }
        if (topicId != null) {
            sb.append("topicId=").append(topicId).append("&");
        }
        if (mode != null) {
            sb.append("mode=").append(mode).append("&");
        }
        if (sb.charAt(sb.length() - 1) == '&' || sb.charAt(sb.length() - 1) == '?') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}

