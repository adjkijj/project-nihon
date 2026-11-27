package com.nihon.controller;

import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.PracticeKanjiAnswerForm;
import com.nihon.dto.PracticeKanjiQuestionDto;
import com.nihon.dto.PracticeKanjiResultDto;
import com.nihon.dto.TopicGroupDto;
import com.nihon.entity.Topic;
import com.nihon.service.KanjiPracticeService;
import com.nihon.service.KanjiService;
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
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Controller phục vụ chế độ Luyện tập Kanji riêng biệt (F22, F23).
 * Hỗ trợ 3 dạng bài: Đoán âm đọc (KANJI_TO_READING), Viết chữ Kanji (MEANING_TO_KANJI),
 * và Trắc nghiệm 4 lựa chọn (KANJI_CHOICE).
 * Chấm điểm trực tiếp tại máy chủ, chống rò rỉ đáp án tại client.
 */
@Controller
@RequestMapping("/practice/kanji")
public class KanjiPracticeController {

    private final KanjiPracticeService kanjiPracticeService;
    private final KanjiService kanjiService;
    private final TopicService topicService;
    private final StudyProgressService studyProgressService;

    public KanjiPracticeController(KanjiPracticeService kanjiPracticeService,
                                   KanjiService kanjiService,
                                   TopicService topicService,
                                   StudyProgressService studyProgressService) {
        this.kanjiPracticeService = kanjiPracticeService;
        this.kanjiService = kanjiService;
        this.topicService = topicService;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Màn hình chọn bài học Kanji và dạng bài luyện tập.
     */
    @GetMapping
    public String index(@RequestParam(value = "topicId", required = false) Long topicId,
                        @RequestParam(value = "mode", required = false, defaultValue = "KANJI_TO_READING") String modeStr,
                        HttpSession session,
                        Model model) {
        KanjiPracticeMode mode = KanjiPracticeMode.fromString(modeStr);
        List<Topic> topics = kanjiService.getKanjiTopics();
        List<TopicGroupDto> topicGroups = kanjiService.getKanjiTopicGroups();

        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicService.findById(topicId).isPresent();
            if (!topicExists) {
                model.addAttribute("errorMessage", "Bài học Kanji không tồn tại");
                model.addAttribute("topics", Collections.emptyList());
                model.addAttribute("topicGroups", Collections.emptyList());
                model.addAttribute("selectedTopicId", topicId);
                model.addAttribute("selectedMode", mode.name());
                model.addAttribute("wrongCount", 0);
                model.addAttribute("activeNav", "practice");
                return "practice/kanji-index";
            }
        }

        int wrongCount = studyProgressService.getWrongKanjiIds(session, mode.getStudyProgressMode(), effectiveTopicId).size();
        int totalWrongAllModes = studyProgressService.getWrongKanjiIds(session, "ALL", effectiveTopicId).size();

        model.addAttribute("topics", topics);
        model.addAttribute("topicGroups", topicGroups);
        model.addAttribute("selectedTopicId", effectiveTopicId);
        model.addAttribute("selectedMode", mode.name());
        model.addAttribute("wrongCount", wrongCount);
        model.addAttribute("totalWrongAllModes", totalWrongAllModes);
        model.addAttribute("activeNav", "practice");

        return "practice/kanji-index";
    }

    /**
     * Hiển thị một câu hỏi luyện tập Kanji ngẫu nhiên.
     */
    @GetMapping("/question")
    public String showQuestion(@RequestParam(value = "topicId", required = false) Long topicId,
                               @RequestParam(value = "mode", required = false, defaultValue = "KANJI_TO_READING") String modeStr,
                               @RequestParam(value = "excludeId", required = false) Long excludeId,
                               @RequestParam(value = "retryWrongOnly", required = false, defaultValue = "false") String retryWrongOnlyParam,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        KanjiPracticeMode mode = KanjiPracticeMode.fromString(modeStr);
        boolean retryWrongOnly = parseRetryWrongOnly(retryWrongOnlyParam);

        Long effectiveTopicId = topicId;
        if (topicId != null && topicId <= 0) {
            effectiveTopicId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        } else if (topicId != null) {
            List<Topic> topics = kanjiService.getKanjiTopics();
            boolean topicExists = topics.stream().anyMatch(t -> topicId.equals(t.getId()))
                    || topicService.findById(topicId).isPresent();
            if (!topicExists) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bài học Kanji không tồn tại");
                return "redirect:/practice/kanji";
            }
        }

        Long effectiveExcludeId = excludeId;
        if (excludeId != null && excludeId <= 0) {
            effectiveExcludeId = null;
            model.addAttribute("errorMessage", "ID không hợp lệ");
        }

        Set<Long> allowedIds = null;
        if (retryWrongOnly) {
            Set<Long> wrongIds = studyProgressService.getWrongKanjiIds(session, mode.getStudyProgressMode(), effectiveTopicId);
            if (wrongIds == null || wrongIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa có chữ Kanji nào làm sai trong phiên này.");
                return "redirect:/practice/kanji" + buildQueryString(effectiveTopicId, mode.name(), false);
            }
            allowedIds = wrongIds;
        }

        Optional<PracticeKanjiQuestionDto> questionOpt = kanjiPracticeService.getNextQuestion(
                effectiveTopicId, mode, effectiveExcludeId, allowedIds, session);

        if (questionOpt.isEmpty()) {
            if (retryWrongOnly) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn chưa có chữ Kanji nào làm sai trong phiên này.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Chưa có đủ dữ liệu Kanji phù hợp để tạo câu hỏi cho dạng bài này.");
            }
            return "redirect:/practice/kanji" + buildQueryString(effectiveTopicId, mode.name(), false);
        }

        PracticeKanjiQuestionDto question = questionOpt.get();
        PracticeKanjiAnswerForm form = PracticeKanjiAnswerForm.builder()
                .kanjiId(question.getKanjiId())
                .mode(mode.name())
                .topicId(effectiveTopicId)
                .retryWrongOnly(retryWrongOnly)
                .build();

        model.addAttribute("question", question);
        model.addAttribute("form", form);
        model.addAttribute("retryWrongOnly", retryWrongOnly);
        model.addAttribute("activeNav", "practice");

        return "practice/kanji-question";
    }

    /**
     * Nhận câu trả lời và chấm điểm trực tiếp trên máy chủ.
     * Chống CSRF thông qua CsrfInterceptor và kiểm tra tính hợp lệ của DTO.
     */
    @PostMapping("/check")
    public String checkAnswer(@Valid @ModelAttribute("form") PracticeKanjiAnswerForm form,
                              BindingResult bindingResult,
                              @RequestParam(value = "retryWrongOnly", required = false, defaultValue = "false") String retryWrongOnlyParam,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        boolean retryWrongOnly = (form != null && form.isRetryWrongOnly()) || parseRetryWrongOnly(retryWrongOnlyParam);
        String modeName = form != null && form.getMode() != null ? form.getMode() : "KANJI_TO_READING";
        Long topicId = form != null ? form.getTopicId() : null;

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu câu trả lời không hợp lệ hoặc vượt quá độ dài quy định.");
            return "redirect:/practice/kanji/question" + buildQueryString(topicId, modeName, retryWrongOnly);
        }

        try {
            PracticeKanjiResultDto result = kanjiPracticeService.checkAnswer(form, session);

            // Ghi nhận tiến độ học tập vào session
            studyProgressService.recordKanjiAttempt(
                    session,
                    result.getKanjiId(),
                    result.getCharacter(),
                    result.getMode().getStudyProgressMode(),
                    result.getTopicId(),
                    result.getTopicName(),
                    result.getUserAnswer(),
                    result.getExpectedAnswer(),
                    result.isCorrect(),
                    result.getFeedbackMessage()
            );

            int wrongCount = studyProgressService.getWrongKanjiIds(session, result.getMode().getStudyProgressMode(), result.getTopicId()).size();
            boolean retryCompleted = retryWrongOnly && wrongCount == 0;

            model.addAttribute("result", result);
            model.addAttribute("retryWrongOnly", retryWrongOnly);
            model.addAttribute("retryCompleted", retryCompleted);
            model.addAttribute("wrongCount", wrongCount);
            model.addAttribute("sessionStats", studyProgressService.getSummary(session, result.getMode().getStudyProgressMode(), result.getTopicId()));
            model.addAttribute("activeNav", "practice");

            return "practice/kanji-result";
        } catch (NoSuchElementException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage() != null ? e.getMessage() : "Mã Kanji không tồn tại hoặc lựa chọn đã hết hạn.");
            return "redirect:/practice/kanji" + buildQueryString(topicId, modeName, retryWrongOnly);
        }
    }

    private boolean parseRetryWrongOnly(String val) {
        if (val == null || val.isBlank()) {
            return false;
        }
        String s = val.strip().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    private String buildQueryString(Long topicId, String mode, boolean retryWrongOnly) {
        StringBuilder sb = new StringBuilder("?");
        boolean hasParam = false;
        if (topicId != null && topicId > 0) {
            sb.append("topicId=").append(topicId);
            hasParam = true;
        }
        if (mode != null && !mode.isBlank()) {
            if (hasParam) sb.append("&");
            sb.append("mode=").append(mode);
            hasParam = true;
        }
        if (retryWrongOnly) {
            if (hasParam) sb.append("&");
            sb.append("retryWrongOnly=true");
        }
        return hasParam ? sb.toString() : "";
    }
}

