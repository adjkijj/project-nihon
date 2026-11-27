package com.nihon.service;

import com.nihon.dto.KanjiChoiceOptionDto;
import com.nihon.dto.KanjiPracticeMode;
import com.nihon.dto.PracticeKanjiAnswerForm;
import com.nihon.dto.PracticeKanjiQuestionDto;
import com.nihon.dto.PracticeKanjiResultDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.repository.KanjiEntryRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dịch vụ luyện tập Kanji riêng biệt (F22, F23).
 * Xử lý tạo câu hỏi, chấm điểm tại máy chủ, chuẩn hóa âm đọc và sinh lựa chọn trắc nghiệm.
 */
@Service
public class KanjiPracticeService {

    public static final String SESSION_KANJI_CHOICE_ID = "KANJI_CHOICE_QUESTION_ID";
    public static final String SESSION_KANJI_CHOICE_OPTION_IDS = "KANJI_CHOICE_OPTION_IDS";

    private static final Pattern DELIMITER_PATTERN = Pattern.compile("[,;、，/\\s]+");

    private final KanjiEntryRepository kanjiEntryRepository;

    public KanjiPracticeService(KanjiEntryRepository kanjiEntryRepository) {
        this.kanjiEntryRepository = kanjiEntryRepository;
    }

    /**
     * Trích xuất và chuẩn hóa tất cả các âm On và Kun hợp lệ của chữ Kanji.
     * Quy tắc chuẩn hóa (F22):
     * - Chuẩn hóa NFC
     * - Bỏ khoảng trắng
     * - Chuyển Katakana thành Hiragana
     * - Loại bỏ dấu "-" và phần sau dấu "-" trong âm Kun (VD: とお-ります -> とお)
     * - Bỏ qua các token rỗng hoặc chỉ chứa dấu gạch nối "-"
     *
     * @param kanji Thực thể KanjiEntry
     * @return Danh sách các âm đọc hợp lệ duy nhất dưới dạng chữ Hiragana
     */
    public List<String> extractValidReadings(KanjiEntry kanji) {
        if (kanji == null) {
            return Collections.emptyList();
        }

        Set<String> readings = new LinkedHashSet<>();

        // 1. Phân tích âm On (thường là Katakana)
        if (kanji.getOnyomi() != null && !kanji.getOnyomi().isBlank()) {
            String[] tokens = DELIMITER_PATTERN.split(kanji.getOnyomi());
            for (String raw : tokens) {
                String normalized = normalizeReading(raw);
                if (!normalized.isBlank()) {
                    readings.add(normalized);
                }
            }
        }

        // 2. Phân tích âm Kun (thường là Hiragana, có thể chứa dấu '-')
        if (kanji.getKunyomi() != null && !kanji.getKunyomi().isBlank()) {
            String[] tokens = DELIMITER_PATTERN.split(kanji.getKunyomi());
            for (String raw : tokens) {
                String normalized = normalizeReading(raw);
                if (!normalized.isBlank()) {
                    readings.add(normalized);
                }
            }
        }

        return new ArrayList<>(readings);
    }

    /**
     * Chuẩn hóa một chuỗi âm đọc hoặc câu trả lời của người học.
     */
    public String normalizeReading(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Chuẩn hóa NFC
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);

        // 2. Bỏ khoảng trắng
        normalized = normalized.replaceAll("\\s+", "");

        // 3. Bỏ dấu '-' và phần sau dấu '-' (nếu có)
        int dashIdx = normalized.indexOf('-');
        if (dashIdx >= 0) {
            normalized = normalized.substring(0, dashIdx);
        }

        // 4. Đổi Katakana thành Hiragana để so sánh
        normalized = RomajiConverter.katakanaToHiragana(normalized);

        return normalized.strip();
    }

    /**
     * Kiểm tra câu trả lời âm đọc của người học có khớp bất kỳ âm On/Kun hợp lệ nào không.
     */
    public boolean isReadingCorrect(String userAnswer, KanjiEntry kanji) {
        if (userAnswer == null || userAnswer.isBlank() || kanji == null) {
            return false;
        }

        String normalizedUserAns = normalizeReading(userAnswer);
        if (normalizedUserAns.isBlank()) {
            return false;
        }

        List<String> validReadings = extractValidReadings(kanji);
        return validReadings.contains(normalizedUserAns);
    }

    /**
     * Kiểm tra Kanji có đủ dữ liệu cho dạng bài hay không.
     */
    public boolean isEligibleForMode(KanjiEntry kanji, KanjiPracticeMode mode, long totalAvailableKanjiCount) {
        if (kanji == null) {
            return false;
        }

        return switch (mode) {
            case KANJI_TO_READING -> !extractValidReadings(kanji).isEmpty();
            case MEANING_TO_KANJI -> hasMeaningOrHanViet(kanji);
            case KANJI_CHOICE -> totalAvailableKanjiCount >= 4 && hasMeaningOrHanViet(kanji);
        };
    }

    private boolean hasMeaningOrHanViet(KanjiEntry kanji) {
        return (kanji.getHanViet() != null && !kanji.getHanViet().isBlank())
                || (kanji.getMeaningVi() != null && !kanji.getMeaningVi().isBlank());
    }

    /**
     * Lấy câu hỏi ngẫu nhiên tiếp theo cho chế độ luyện tập Kanji.
     */
    public Optional<PracticeKanjiQuestionDto> getNextQuestion(Long topicId,
                                                             KanjiPracticeMode mode,
                                                             Long excludeId,
                                                             Set<Long> allowedKanjiIds,
                                                             HttpSession session) {
        if (mode == null) {
            mode = KanjiPracticeMode.KANJI_TO_READING;
        }

        List<KanjiEntry> pool;
        if (allowedKanjiIds != null && !allowedKanjiIds.isEmpty()) {
            pool = kanjiEntryRepository.findEligibleKanjiWithIds(topicId, allowedKanjiIds);
        } else {
            pool = kanjiEntryRepository.findEligibleKanji(topicId);
        }

        if (pool == null || pool.isEmpty()) {
            return Optional.empty();
        }

        final KanjiPracticeMode effectiveMode = mode;
        long totalAvailable = pool.size();

        // Lọc các Kanji đủ điều kiện dữ liệu theo dạng bài
        List<KanjiEntry> eligible = pool.stream()
                .filter(k -> isEligibleForMode(k, effectiveMode, totalAvailable))
                .toList();

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        // Loại trừ câu vừa làm nếu có nhiều hơn 1 lựa chọn
        List<KanjiEntry> candidateList = eligible;
        if (excludeId != null && eligible.size() > 1) {
            List<KanjiEntry> excluded = eligible.stream()
                    .filter(k -> !excludeId.equals(k.getId()))
                    .toList();
            if (!excluded.isEmpty()) {
                candidateList = excluded;
            }
        }

        KanjiEntry selected = candidateList.get(ThreadLocalRandom.current().nextInt(candidateList.size()));

        PracticeKanjiQuestionDto question = PracticeKanjiQuestionDto.builder()
                .kanjiId(selected.getId())
                .character(selected.getCharacter())
                .mode(effectiveMode)
                .topicId(selected.getTopic() != null ? selected.getTopic().getId() : topicId)
                .topicName(selected.getTopic() != null ? selected.getTopic().getName() : "Tất cả bài học Kanji")
                .build();

        switch (effectiveMode) {
            case KANJI_TO_READING -> {
                List<String> readings = extractValidReadings(selected);
                question.setValidReadingsCount(readings.size());
            }
            case MEANING_TO_KANJI -> {
                question.setPromptHanViet(selected.getHanViet() != null ? selected.getHanViet().strip() : "");
                question.setPromptMeaningVi(selected.getMeaningVi() != null ? selected.getMeaningVi().strip() : "");
            }
            case KANJI_CHOICE -> {
                List<KanjiChoiceOptionDto> options = generateChoiceOptions(selected, pool, session);
                question.setOptions(options);
            }
        }

        return Optional.of(question);
    }

    /**
     * Sinh 4 lựa chọn trắc nghiệm (1 đáp án đúng + 3 đáp án nhiễu) và lưu vào session.
     */
    private List<KanjiChoiceOptionDto> generateChoiceOptions(KanjiEntry correctKanji,
                                                             List<KanjiEntry> localPool,
                                                             HttpSession session) {
        // Thu thập các đáp án nhiễu có Hán Việt hoặc Nghĩa khác với Kanji đúng
        List<KanjiEntry> distractors = localPool.stream()
                .filter(k -> !Objects.equals(k.getId(), correctKanji.getId()) && hasMeaningOrHanViet(k))
                .collect(Collectors.toCollection(ArrayList::new));

        // Nếu trong pool hiện tại không đủ 3 đáp án nhiễu, lấy bù từ toàn bộ bảng kanji_entry
        if (distractors.size() < 3) {
            List<KanjiEntry> allKanji = kanjiEntryRepository.findAll();
            for (KanjiEntry extra : allKanji) {
                if (!Objects.equals(extra.getId(), correctKanji.getId())
                        && hasMeaningOrHanViet(extra)
                        && distractors.stream().noneMatch(d -> Objects.equals(d.getId(), extra.getId()))) {
                    distractors.add(extra);
                    if (distractors.size() >= 3) {
                        break;
                    }
                }
            }
        }

        Collections.shuffle(distractors);
        List<KanjiEntry> selectedDistractors = distractors.subList(0, Math.min(3, distractors.size()));

        List<KanjiChoiceOptionDto> options = new ArrayList<>();
        options.add(toChoiceOption(correctKanji));
        for (KanjiEntry d : selectedDistractors) {
            options.add(toChoiceOption(d));
        }

        Collections.shuffle(options);

        // Lưu danh sách ID các lựa chọn hợp lệ vào session để xác thực phía server
        if (session != null) {
            List<Long> optionIds = options.stream().map(KanjiChoiceOptionDto::getKanjiId).toList();
            session.setAttribute(SESSION_KANJI_CHOICE_ID, correctKanji.getId());
            session.setAttribute(SESSION_KANJI_CHOICE_OPTION_IDS, optionIds);
        }

        return options;
    }

    private KanjiChoiceOptionDto toChoiceOption(KanjiEntry kanji) {
        StringBuilder display = new StringBuilder();
        if (kanji.getHanViet() != null && !kanji.getHanViet().isBlank()) {
            display.append(kanji.getHanViet().strip());
        }
        if (kanji.getMeaningVi() != null && !kanji.getMeaningVi().isBlank()) {
            if (display.length() > 0) {
                display.append(" - ");
            }
            display.append(kanji.getMeaningVi().strip());
        }
        if (display.length() == 0) {
            display.append(kanji.getCharacter());
        }

        return KanjiChoiceOptionDto.builder()
                .kanjiId(kanji.getId())
                .character(kanji.getCharacter())
                .displayText(display.toString())
                .build();
    }

    /**
     * Chấm điểm câu trả lời luyện tập Kanji trực tiếp tại máy chủ.
     */
    public PracticeKanjiResultDto checkAnswer(PracticeKanjiAnswerForm form, HttpSession session) {
        if (form == null || form.getKanjiId() == null) {
            throw new IllegalArgumentException("Dữ liệu câu trả lời không hợp lệ.");
        }

        KanjiEntry kanji = kanjiEntryRepository.findById(form.getKanjiId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy Kanji với mã ID: " + form.getKanjiId()));

        KanjiPracticeMode mode = form.getResolvedMode();
        boolean correct = false;
        String userAnswer = form.getAnswer() != null ? form.getAnswer().strip() : "";
        String expectedAnswer = "";
        String feedbackMessage = "";
        List<String> validReadings = Collections.emptyList();

        switch (mode) {
            case KANJI_TO_READING -> {
                validReadings = extractValidReadings(kanji);
                correct = isReadingCorrect(form.getAnswer(), kanji);
                expectedAnswer = String.join(", ", validReadings);
                if (correct) {
                    feedbackMessage = "Chính xác! Âm đọc của " + kanji.getCharacter() + " là: " + expectedAnswer;
                } else {
                    feedbackMessage = "Chưa chính xác. Các âm đọc hợp lệ của " + kanji.getCharacter() + " gồm: " + expectedAnswer;
                }
            }
            case MEANING_TO_KANJI -> {
                String normalizedUserAns = Normalizer.normalize(userAnswer, Normalizer.Form.NFC).replaceAll("\\s+", "");
                String expectedChar = Normalizer.normalize(kanji.getCharacter(), Normalizer.Form.NFC).replaceAll("\\s+", "");
                correct = normalizedUserAns.equals(expectedChar);
                expectedAnswer = kanji.getCharacter();
                if (kanji.getHanViet() != null && !kanji.getHanViet().isBlank()) {
                    expectedAnswer += " (" + kanji.getHanViet().strip() + ")";
                }
                if (correct) {
                    feedbackMessage = "Chính xác! Chữ Kanji cần viết là " + kanji.getCharacter();
                } else {
                    feedbackMessage = "Chưa chính xác. Chữ Kanji đúng là: " + kanji.getCharacter();
                }
            }
            case KANJI_CHOICE -> {
                if (session == null) {
                    throw new IllegalArgumentException("Phiên làm việc đã hết hạn.");
                }

                Long sessionKanjiId = (Long) session.getAttribute(SESSION_KANJI_CHOICE_ID);
                @SuppressWarnings("unchecked")
                List<Long> sessionOptionIds = (List<Long>) session.getAttribute(SESSION_KANJI_CHOICE_OPTION_IDS);

                if (sessionKanjiId == null || sessionOptionIds == null || !sessionKanjiId.equals(form.getKanjiId())) {
                    throw new IllegalArgumentException("Câu hỏi đã hết hạn hoặc bạn đã nộp câu hỏi này rồi.");
                }

                if (form.getChoiceKanjiId() == null || !sessionOptionIds.contains(form.getChoiceKanjiId())) {
                    throw new IllegalArgumentException("Lựa chọn không hợp lệ hoặc nằm ngoài danh sách.");
                }

                correct = form.getChoiceKanjiId().equals(kanji.getId());

                // Lấy thông tin hiển thị của lựa chọn người học và đáp án chuẩn
                KanjiEntry selectedEntry = kanjiEntryRepository.findById(form.getChoiceKanjiId()).orElse(null);
                userAnswer = selectedEntry != null ? toChoiceOption(selectedEntry).getDisplayText() : "Không xác định";
                expectedAnswer = toChoiceOption(kanji).getDisplayText();

                if (correct) {
                    feedbackMessage = "Chính xác! " + kanji.getCharacter() + " là " + expectedAnswer;
                } else {
                    feedbackMessage = "Chưa chính xác. Đáp án đúng của " + kanji.getCharacter() + " là: " + expectedAnswer;
                }

                // Xóa session trắc nghiệm sau khi chấm
                session.removeAttribute(SESSION_KANJI_CHOICE_ID);
                session.removeAttribute(SESSION_KANJI_CHOICE_OPTION_IDS);
            }
        }

        return PracticeKanjiResultDto.builder()
                .kanjiId(kanji.getId())
                .character(kanji.getCharacter())
                .mode(mode)
                .topicId(kanji.getTopic() != null ? kanji.getTopic().getId() : form.getTopicId())
                .topicName(kanji.getTopic() != null ? kanji.getTopic().getName() : "Tất cả bài học Kanji")
                .userAnswer(userAnswer)
                .expectedAnswer(expectedAnswer)
                .allValidReadings(validReadings)
                .correct(correct)
                .feedbackMessage(feedbackMessage)
                .hanViet(kanji.getHanViet())
                .meaningVi(kanji.getMeaningVi())
                .onyomi(kanji.getOnyomi())
                .kunyomi(kanji.getKunyomi())
                .build();
    }
}

