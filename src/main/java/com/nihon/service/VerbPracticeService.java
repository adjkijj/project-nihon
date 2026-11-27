package com.nihon.service;

import com.nihon.conjugation.ConjugatedForms;
import com.nihon.conjugation.TargetForm;
import com.nihon.conjugation.VerbConjugator;
import com.nihon.dto.verb.PracticeVerbAnswerForm;
import com.nihon.dto.verb.PracticeVerbQuestionDto;
import com.nihon.dto.verb.PracticeVerbResultDto;
import com.nihon.entity.Verb;
import com.nihon.repository.VerbRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Dịch vụ luyện tập chia thể động từ tiếng Nhật (F13 - F18).
 * Quản lý sinh câu hỏi, chấm điểm tại máy chủ, sinh lựa chọn trắc nghiệm và ghi nhận tiến độ.
 */
@Service
@Transactional(readOnly = true)
public class VerbPracticeService {

    public static final String SESSION_VERB_CHOICES_PREFIX = "VERB_PRACTICE_CHOICES_";
    public static final String SESSION_VERB_STREAK = "VERB_PRACTICE_STREAK";

    private final VerbRepository verbRepository;
    private final StudyProgressService studyProgressService;
    private final Random random = new Random();

    public VerbPracticeService(VerbRepository verbRepository, StudyProgressService studyProgressService) {
        this.verbRepository = verbRepository;
        this.studyProgressService = studyProgressService;
    }

    /**
     * Sinh câu hỏi luyện chia thể tiếp theo.
     * Tuyệt đối không chứa đáp án trong DTO trả về.
     */
    public Optional<PracticeVerbQuestionDto> getNextQuestion(TargetForm targetForm,
                                                            Integer verbGroup,
                                                            Long topicId,
                                                            String modeStr,
                                                            Long excludeId,
                                                            boolean retryWrongOnly,
                                                            HttpSession session) {
        List<Verb> pool = (topicId != null && topicId > 0)
                ? verbRepository.findByTopicId(topicId)
                : verbRepository.findAll();

        if (pool.isEmpty()) {
            return Optional.empty();
        }

        // Lọc theo nhóm động từ nếu có
        if (verbGroup != null && verbGroup >= 1 && verbGroup <= 3) {
            pool = pool.stream()
                    .filter(v -> v.getVerbGroup() != null && v.getVerbGroup().equals(verbGroup))
                    .toList();
        }

        // Lọc chỉ từ làm sai trong phiên nếu yêu cầu
        if (retryWrongOnly && session != null) {
            String filterMode = (targetForm != null && targetForm != TargetForm.RANDOM)
                    ? "VERB_" + targetForm.name()
                    : "ALL";
            Set<Long> wrongIds = studyProgressService.getWrongVerbIds(session, filterMode, topicId);
            if (wrongIds == null || wrongIds.isEmpty()) {
                return Optional.empty();
            }
            pool = pool.stream()
                    .filter(v -> wrongIds.contains(v.getId()))
                    .toList();
        }

        if (pool.isEmpty()) {
            return Optional.empty();
        }

        // Tránh lặp lại câu hỏi vừa trả lời nếu có nhiều hơn 1 câu
        List<Verb> candidates = pool;
        if (excludeId != null && pool.size() > 1) {
            candidates = pool.stream()
                    .filter(v -> !v.getId().equals(excludeId))
                    .toList();
        }
        if (candidates.isEmpty()) {
            candidates = pool;
        }

        Verb selected = candidates.get(random.nextInt(candidates.size()));

        // Xác định thể đích thực tế cho câu hỏi này
        TargetForm effectiveTarget = (targetForm == null || targetForm == TargetForm.RANDOM)
                ? pickRandomTargetForm()
                : targetForm;

        String safeMode = (modeStr != null && "CHOICE".equalsIgnoreCase(modeStr.trim())) ? "CHOICE" : "INPUT";

        List<String> choices = Collections.emptyList();
        if ("CHOICE".equals(safeMode) && session != null) {
            choices = VerbConjugator.generateChoices(
                    selected.getDictionaryForm(),
                    selected.getVerbGroup(),
                    selected.getKanjiText(),
                    effectiveTarget
            );
            session.setAttribute(SESSION_VERB_CHOICES_PREFIX + selected.getId() + "_" + effectiveTarget.name(), choices);
        }

        // Xây dựng đề bài hiển thị
        String prompt;
        if (effectiveTarget == TargetForm.MASU) {
            prompt = selected.getDictionaryForm()
                    + (selected.getKanjiText() != null ? " (" + selected.getKanjiText() + ")" : "")
                    + " : " + selected.getMeaningVi();
        } else {
            prompt = selected.getMasuForm()
                    + (selected.getKanjiText() != null ? " (" + selected.getKanjiText() + ")" : "")
                    + " : " + selected.getMeaningVi();
        }

        int currentStreak = getStreak(session);

        PracticeVerbQuestionDto question = PracticeVerbQuestionDto.builder()
                .verbId(selected.getId())
                .masuForm(selected.getMasuForm())
                .dictionaryForm(selected.getDictionaryForm())
                .kanjiText(selected.getKanjiText())
                .meaningVi(selected.getMeaningVi())
                .verbGroup(selected.getVerbGroup())
                .targetForm(effectiveTarget)
                .prompt(prompt)
                .speechText(selected.getMasuForm())
                .mode(safeMode)
                .choices(choices)
                .topicId(topicId)
                .group(verbGroup)
                .retryWrongOnly(retryWrongOnly)
                .currentStreak(currentStreak)
                .build();

        return Optional.of(question);
    }

    /**
     * Chấm điểm câu trả lời chia động từ trực tiếp tại máy chủ.
     */
    public PracticeVerbResultDto checkAnswer(PracticeVerbAnswerForm form, HttpSession session) {
        if (form == null || form.getVerbId() == null) {
            throw new IllegalArgumentException("Dữ liệu câu trả lời không hợp lệ.");
        }

        Verb verb = verbRepository.findById(form.getVerbId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy động từ với ID: " + form.getVerbId()));

        TargetForm targetForm = form.getResolvedTargetForm();
        String safeMode = (form.getMode() != null && "CHOICE".equalsIgnoreCase(form.getMode().trim())) ? "CHOICE" : "INPUT";

        String userAnswer = form.getAnswer() != null ? form.getAnswer().trim() : "";

        // Nếu là trắc nghiệm, trích xuất text từ phương án đã chọn trong session
        if ("CHOICE".equals(safeMode) && session != null) {
            @SuppressWarnings("unchecked")
            List<String> storedChoices = (List<String>) session.getAttribute(
                    SESSION_VERB_CHOICES_PREFIX + verb.getId() + "_" + targetForm.name()
            );
            if (storedChoices != null && form.getChoiceIndex() != null
                    && form.getChoiceIndex() >= 0 && form.getChoiceIndex() < storedChoices.size()) {
                userAnswer = storedChoices.get(form.getChoiceIndex());
            }
        }

        ConjugatedForms forms = VerbConjugator.conjugate(
                verb.getDictionaryForm(),
                verb.getVerbGroup(),
                verb.getKanjiText()
        );

        boolean correct = VerbConjugator.isFormMatch(
                verb.getDictionaryForm(),
                verb.getVerbGroup(),
                verb.getKanjiText(),
                targetForm,
                userAnswer
        );

        // Cập nhật chuỗi làm đúng liên tiếp
        int streak = getStreak(session);
        if (correct) {
            streak++;
        } else {
            streak = 0;
        }
        setStreak(session, streak);

        String expectedKana = VerbConjugator.getFormKana(forms, targetForm);
        String expectedKanji = VerbConjugator.getFormKanji(forms, targetForm);

        String feedbackRule = correct
                ? "Chính xác! Thể " + targetForm.getShortLabel() + " của động từ là: " + expectedKana + (expectedKanji != null ? " (" + expectedKanji + ")" : "")
                : VerbConjugator.getShortRule(verb.getVerbGroup(), targetForm, verb.getDictionaryForm());

        String prompt = verb.getMasuForm() + (verb.getKanjiText() != null ? " (" + verb.getKanjiText() + ")" : "");

        // Ghi nhận vào StudyProgressService
        if (session != null) {
            String expDisplay = expectedKana + (expectedKanji != null && !expectedKanji.equals(expectedKana) ? " (" + expectedKanji + ")" : "");
            studyProgressService.recordVerbAttempt(
                    session,
                    verb.getId(),
                    targetForm.name(),
                    verb.getTopic() != null ? verb.getTopic().getId() : null,
                    verb.getTopic() != null ? verb.getTopic().getName() : "Tất cả chủ đề động từ",
                    prompt,
                    userAnswer,
                    expDisplay,
                    correct,
                    feedbackRule
            );
        }

        return PracticeVerbResultDto.builder()
                .verbId(verb.getId())
                .prompt(prompt)
                .targetForm(targetForm)
                .userAnswer(userAnswer)
                .expectedKana(expectedKana)
                .expectedKanji(expectedKanji)
                .correct(correct)
                .feedbackRule(feedbackRule)
                .verbGroup(verb.getVerbGroup())
                .meaningVi(verb.getMeaningVi())
                .mode(safeMode)
                .topicId(form.getTopicId())
                .group(form.getGroup())
                .retryWrongOnly(form.isRetryWrongOnly())
                .currentStreak(streak)
                .build();
    }

    private TargetForm pickRandomTargetForm() {
        TargetForm[] forms = new TargetForm[]{TargetForm.TE, TargetForm.RU, TargetForm.TA, TargetForm.NAI, TargetForm.MASU};
        return forms[random.nextInt(forms.length)];
    }

    private int getStreak(HttpSession session) {
        if (session == null) return 0;
        Object val = session.getAttribute(SESSION_VERB_STREAK);
        return (val instanceof Integer i) ? i : 0;
    }

    private void setStreak(HttpSession session, int streak) {
        if (session != null) {
            session.setAttribute(SESSION_VERB_STREAK, streak);
        }
    }
}

