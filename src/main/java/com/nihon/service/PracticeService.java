package com.nihon.service;

import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeChoiceAnswerForm;
import com.nihon.dto.PracticeChoiceOptionDto;
import com.nihon.dto.PracticeChoiceQuestionDto;
import com.nihon.dto.PracticeChoiceResultDto;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.PracticeViJaAnswerForm;
import com.nihon.dto.PracticeViJaQuestionDto;
import com.nihon.dto.PracticeViJaResultDto;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.VocabularyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dịch vụ kiểm tra phản xạ từ vựng tiếng Nhật sang tiếng Việt.
 * Áp dụng quy tắc chấm điểm server-side an toàn, chuẩn hóa Unicode,
 * bảo toàn dữ liệu nguồn và không làm lộ đáp án trước khi nộp.
 */
@Service
@Transactional(readOnly = true)
public class PracticeService {

    private final VocabularyRepository vocabularyRepository;

    public PracticeService(VocabularyRepository vocabularyRepository) {
        this.vocabularyRepository = vocabularyRepository;
    }

    /**
     * Lấy một câu hỏi từ vựng ngẫu nhiên theo chủ đề (hoặc tất cả các chủ đề).
     * Tránh lặp lại câu hỏi vừa làm nếu chủ đề có nhiều hơn 1 từ.
     */
    public Optional<PracticeQuestionDto> getNextQuestion(Long topicId, Long excludeVocabId) {
        return getNextQuestion(topicId, excludeVocabId, null);
    }

    /**
     * Lấy một câu hỏi từ vựng ngẫu nhiên theo chủ đề, giới hạn trong tập id cho phép (ví dụ tập từ làm sai).
     */
    public Optional<PracticeQuestionDto> getNextQuestion(Long topicId, Long excludeVocabId, Set<Long> allowedIds) {
        List<Long> ids = vocabularyRepository.findIdsByTopicId(topicId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        if (allowedIds != null) {
            ids = ids.stream().filter(allowedIds::contains).toList();
            if (ids.isEmpty()) {
                return Optional.empty();
            }
        }

        List<Long> candidates = ids;
        if (ids.size() > 1 && excludeVocabId != null) {
            candidates = ids.stream()
                    .filter(id -> !id.equals(excludeVocabId))
                    .toList();
            if (candidates.isEmpty()) {
                candidates = ids;
            }
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        Long selectedId = candidates.get(randomIndex);

        return vocabularyRepository.findById(selectedId)
                .map(v -> PracticeQuestionDto.fromEntity(v, topicId));
    }

    /**
     * Chấm điểm câu trả lời của người học phía máy chủ.
     * Kiểm tra cách đọc Hiragana (đối với từ có Kanji và có dữ liệu) và nghĩa tiếng Việt.
     */
    public PracticeResultDto checkAnswer(PracticeAnswerForm form) {
        if (form.getVocabId() == null || form.getVocabId() <= 0) {
            throw new IllegalArgumentException("Mã từ vựng không hợp lệ.");
        }

        Vocabulary v = vocabularyRepository.findById(form.getVocabId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với mã: " + form.getVocabId()));

        Long effectiveTopicId = form.getTopicId();
        if (effectiveTopicId != null && (v.getTopic() == null || !effectiveTopicId.equals(v.getTopic().getId()))) {
            effectiveTopicId = null;
        }

        boolean hasKanji = v.isHasKanji();
        boolean hasReadingInSource = v.getReading() != null && !v.getReading().isBlank();
        boolean requireReading = hasKanji && hasReadingInSource;
        boolean readingMissingInSource = hasKanji && !hasReadingInSource;

        String rawUserReading = form.getUserReading();
        String rawUserMeaning = form.getUserMeaning();

        // 1. Chấm phần cách đọc Hiragana
        Boolean readingCorrect = null;
        List<String> acceptedReadings = Collections.emptyList();

        if (requireReading) {
            acceptedReadings = parseAcceptedReadings(v.getReading());
            String normalizedUserReading = normalizeReadingInput(rawUserReading);

            if (normalizedUserReading.isEmpty()) {
                readingCorrect = false;
            } else {
                readingCorrect = acceptedReadings.stream()
                        .anyMatch(r -> normalizeReadingInput(r).equals(normalizedUserReading));
            }
        }

        // 2. Chấm phần nghĩa tiếng Việt
        List<String> acceptedMeanings = parseAcceptedMeanings(v.getMeaningVi());
        String normalizedUserMeaning = normalizeMeaningInput(rawUserMeaning);

        boolean meaningCorrect;
        if (normalizedUserMeaning.isEmpty()) {
            meaningCorrect = false;
        } else {
            meaningCorrect = matchesAnyMeaning(normalizedUserMeaning, acceptedMeanings, v.getMeaningVi());
        }

        // 3. Kết quả tổng thể
        boolean overallCorrect = meaningCorrect && (readingCorrect == null || readingCorrect);

        // Xây dựng thông điệp phản hồi
        String feedbackMessage = buildFeedbackMessage(overallCorrect, readingCorrect, meaningCorrect, requireReading, readingMissingInSource);

        return PracticeResultDto.builder()
                .vocabId(v.getId())
                .japaneseText(v.getJapaneseText())
                .topicName(v.getTopic() != null ? v.getTopic().getName() : "Tất cả chủ đề")
                .topicId(effectiveTopicId)
                .hasKanji(hasKanji)
                .requireReading(requireReading)
                .readingMissingInSource(readingMissingInSource)
                .userReading(rawUserReading != null ? rawUserReading.strip() : "")
                .userMeaning(rawUserMeaning != null ? rawUserMeaning.strip() : "")
                .readingCorrect(readingCorrect)
                .meaningCorrect(meaningCorrect)
                .overallCorrect(overallCorrect)
                .expectedReading(v.getReading() != null ? v.getReading().strip() : "")
                .expectedMeaning(v.getMeaningVi() != null ? v.getMeaningVi().strip() : "")
                .acceptedReadings(acceptedReadings)
                .acceptedMeanings(acceptedMeanings)
                .feedbackMessage(feedbackMessage)
                .build();
    }

    /**
     * Tách các cách đọc được khai báo rõ ràng trong dữ liệu nguồn.
     * Chấp nhận các dấu phân cách: dấu phẩy (phương Tây hoặc tiếng Nhật), gạch chéo (/), gạch đứng (|).
     */
    public List<String> parseAcceptedReadings(String reading) {
        if (reading == null || reading.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(reading.split("[,/、|]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Tách các nghĩa tiếng Việt hợp lệ được phân tách trong dữ liệu nguồn.
     */
    public List<String> parseAcceptedMeanings(String meaningVi) {
        if (meaningVi == null || meaningVi.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> set = new LinkedHashSet<>();
        String[] parts = meaningVi.split("[,;/|]");
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
                // Nếu có phần giải thích trong ngoặc đơn, chấp nhận cả phần nghĩa khi lược bỏ ngoặc
                // ví dụ: "Tôi (đại từ xưng hô)" -> chấp nhận cả "Tôi"
                String withoutParens = trimmed.replaceAll("\\([^)]*\\)", "").strip();
                if (!withoutParens.isEmpty() && !withoutParens.equalsIgnoreCase(trimmed)) {
                    set.add(withoutParens);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * Chuẩn hóa Unicode (NFC), loại bỏ khoảng trắng đầu/cuối và thu gọn khoảng trắng bên trong.
     * Giữ nguyên chữ cái Hiragana/Katakana (không tự chuyển đổi sang Kanji hay ngược lại).
     */
    public String normalizeReadingInput(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        return normalized.replaceAll("\\s+", "");
    }

    /**
     * Chuẩn hóa nghĩa tiếng Việt: Unicode NFC, strip, chữ thường, thu gọn khoảng trắng.
     */
    public String normalizeMeaningInput(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ");
    }

    private boolean matchesAnyMeaning(String normalizedUserMeaning, List<String> acceptedMeanings, String fullRawMeaning) {
        // So khớp với toàn bộ chuỗi nghĩa gốc
        String normalizedFull = normalizeMeaningInput(fullRawMeaning);
        if (normalizedFull.equals(normalizedUserMeaning)) {
            return true;
        }

        // So khớp với từng nghĩa riêng biệt đã tách
        for (String m : acceptedMeanings) {
            if (normalizeMeaningInput(m).equals(normalizedUserMeaning)) {
                return true;
            }
        }

        return false;
    }

    private String buildFeedbackMessage(boolean overallCorrect, Boolean readingCorrect, boolean meaningCorrect,
                                     boolean requireReading, boolean readingMissingInSource) {
        if (overallCorrect) {
            return "Chính xác! Bạn đã ghi nhớ từ này rất tốt.";
        }

        if (requireReading) {
            if (Boolean.TRUE.equals(readingCorrect) && !meaningCorrect) {
                return "Bạn đã đọc đúng Hiragana nhưng nghĩa tiếng Việt chưa chính xác.";
            } else if (Boolean.FALSE.equals(readingCorrect) && meaningCorrect) {
                return "Nghĩa tiếng Việt đã đúng nhưng cách đọc Hiragana chưa chính xác.";
            } else {
                return "Chưa chính xác cả cách đọc Hiragana và nghĩa tiếng Việt.";
            }
        } else if (readingMissingInSource) {
            return meaningCorrect ? "Chính xác nghĩa tiếng Việt!" : "Nghĩa tiếng Việt chưa chính xác.";
        } else {
            return meaningCorrect ? "Chính xác!" : "Nghĩa tiếng Việt chưa chính xác.";
        }
    }

    /**
     * Lấy một câu hỏi từ vựng ngẫu nhiên cho chế độ Việt → Nhật.
     * Chỉ trả về nghĩa tiếng Việt và tiêu đề chủ đề, không trả về từ tiếng Nhật.
     */
    public Optional<PracticeViJaQuestionDto> getReverseQuestion(Long topicId, Long excludeVocabId) {
        return getReverseQuestion(topicId, excludeVocabId, null);
    }

    /**
     * Lấy một câu hỏi từ vựng ngẫu nhiên cho chế độ Việt → Nhật, giới hạn trong tập id cho phép.
     */
    public Optional<PracticeViJaQuestionDto> getReverseQuestion(Long topicId, Long excludeVocabId, Set<Long> allowedIds) {
        List<Long> ids = vocabularyRepository.findIdsByTopicId(topicId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        if (allowedIds != null) {
            ids = ids.stream().filter(allowedIds::contains).toList();
            if (ids.isEmpty()) {
                return Optional.empty();
            }
        }

        List<Long> candidates = ids;
        if (ids.size() > 1 && excludeVocabId != null) {
            candidates = ids.stream()
                    .filter(id -> !id.equals(excludeVocabId))
                    .toList();
            if (candidates.isEmpty()) {
                candidates = ids;
            }
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        Long selectedId = candidates.get(randomIndex);

        return vocabularyRepository.findById(selectedId)
                .map(v -> PracticeViJaQuestionDto.fromEntity(v, topicId));
    }

    /**
     * Chấm điểm câu trả lời chế độ Việt → Nhật tại máy chủ.
     * Yêu cầu đúng cách viết tiếng Nhật (bao gồm Kanji nếu là dạng chuẩn).
     * Không tự thay Kanji bằng Hiragana hoặc Romaji.
     */
    public PracticeViJaResultDto checkReverseAnswer(PracticeViJaAnswerForm form) {
        if (form.getVocabId() == null || form.getVocabId() <= 0) {
            throw new IllegalArgumentException("Mã từ vựng không hợp lệ.");
        }

        Vocabulary v = vocabularyRepository.findById(form.getVocabId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với mã: " + form.getVocabId()));

        Long effectiveTopicId = form.getTopicId();
        if (effectiveTopicId != null && (v.getTopic() == null || !effectiveTopicId.equals(v.getTopic().getId()))) {
            effectiveTopicId = null;
        }

        List<String> acceptedVariants = parseAcceptedJapaneseVariants(v.getJapaneseText());
        String normalizedUserInput = normalizeJapaneseInput(form.getUserJapanese());

        boolean correct = false;
        if (!normalizedUserInput.isEmpty()) {
            correct = acceptedVariants.stream()
                    .anyMatch(variant -> normalizeJapaneseInput(variant).equals(normalizedUserInput));
        }

        String feedbackMessage;
        if (correct) {
            feedbackMessage = "Chính xác! Bạn đã viết đúng từ tiếng Nhật.";
        } else {
            // Kiểm tra xem người học có gõ đúng âm Hiragana nhưng thiếu Kanji không
            String normalizedReading = v.getReading() != null ? normalizeJapaneseInput(v.getReading()) : "";
            if (v.isHasKanji() && !normalizedReading.isEmpty() && normalizedReading.equals(normalizedUserInput)) {
                feedbackMessage = "Chưa chính xác! Bạn đã gõ Hiragana, nhưng dạng chuẩn của từ này yêu cầu viết bằng chữ Hán (Kanji).";
            } else {
                feedbackMessage = "Chưa chính xác! Hãy đối chiếu với đáp án chuẩn bên dưới.";
            }
        }

        return PracticeViJaResultDto.builder()
                .vocabId(v.getId())
                .meaningVi(v.getMeaningVi())
                .userJapanese(form.getUserJapanese() != null ? form.getUserJapanese().strip() : "")
                .expectedJapanese(v.getJapaneseText())
                .expectedReading(v.getReading())
                .acceptedVariants(acceptedVariants)
                .correct(correct)
                .topicName(v.getTopic() != null ? v.getTopic().getName() : "Tất cả chủ đề")
                .topicId(effectiveTopicId)
                .feedbackMessage(feedbackMessage)
                .build();
    }

    /**
     * Phân tích các biến thể tiếng Nhật hợp lệ từ dữ liệu nguồn.
     * Tách các nhánh biến thể (phân cách bởi " or<br />", " or ", " / ").
     * Chấp nhận dạng chuẩn lược bỏ ngoặc Furigana (ví dụ: "私" từ "私（わたし）").
     * Không tự thay thế Kanji bằng Hiragana nếu dạng chuẩn có Kanji.
     */
    public List<String> parseAcceptedJapaneseVariants(String japaneseText) {
        if (japaneseText == null || japaneseText.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> variants = new LinkedHashSet<>();
        // Tách các biến thể được khai báo rõ ràng bằng " or<br />", "<br />", " or ", " / "
        String[] branches = japaneseText.split("\\s+or<br\\s*/?>\\s*|<br\\s*/?>|\\s+or\\s+|\\s+/\\s+");

        for (String branch : branches) {
            String trimmedBranch = branch.strip();
            if (trimmedBranch.isEmpty()) {
                continue;
            }

            String nfcBranch = Normalizer.normalize(trimmedBranch, Normalizer.Form.NFC).strip();
            variants.add(nfcBranch);

            // Bỏ furigana trong ngoặc: [（(][^）)]*[）)]
            String withoutFurigana = nfcBranch.replaceAll("[（(][^）)]*[）)]", "").strip();
            if (!withoutFurigana.isEmpty() && !withoutFurigana.equals(nfcBranch)) {
                variants.add(withoutFurigana);
            }

            // Nếu trong ngoặc có chứa Kanji (ví dụ ～さい（～歳）)
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[（(]([^）)]*)[）)]").matcher(nfcBranch);
            while (m.find()) {
                String inner = m.group(1).strip();
                if (containsKanji(inner)) {
                    variants.add(inner);
                }
            }
        }

        return new ArrayList<>(variants);
    }

    /**
     * Chuẩn hóa Unicode NFC và loại bỏ khoảng trắng đầu/cuối của từ tiếng Nhật.
     */
    public String normalizeJapaneseInput(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        return normalized.replaceAll("\\s+", " ");
    }

    private boolean containsKanji(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * Sinh câu hỏi trắc nghiệm 4 lựa chọn phía máy chủ.
     * Chọn 1 từ ngẫu nhiên theo chủ đề (tránh lặp excludeVocabId).
     * Tạo 3 đáp án nhiễu từ cùng chủ đề, thiếu thì bổ sung từ toàn bộ từ vựng hệ thống.
     * Loại bỏ các đáp án nhiễu trùng nội dung đáp án đúng, trùng nhau hoặc rỗng.
     * Nếu toàn hệ thống không đủ 4 lựa chọn khác biệt thì trả về Optional.empty().
     */
    public Optional<PracticeChoiceQuestionDto> getChoiceQuestion(Long topicId, String direction, Long excludeVocabId) {
        String safeDirection = "VI_JA".equalsIgnoreCase(direction) ? "VI_JA" : "JA_VI";

        // 1. Lấy danh sách từ vựng đủ điều kiện trong chủ đề chỉ định (hoặc toàn bộ)
        List<Vocabulary> topicVocabs = (topicId != null && topicId > 0)
                ? vocabularyRepository.findByTopicIdOrderBySourceLineAsc(topicId)
                : vocabularyRepository.findAll();

        List<Vocabulary> eligibleTargetCandidates = topicVocabs.stream()
                .filter(this::isEligibleForChoice)
                .toList();

        if (eligibleTargetCandidates.isEmpty()) {
            return Optional.empty();
        }

        // Tránh lặp lại câu hỏi vừa hỏi nếu có nhiều hơn 1 từ
        List<Vocabulary> targetPool = eligibleTargetCandidates;
        if (eligibleTargetCandidates.size() > 1 && excludeVocabId != null) {
            List<Vocabulary> filtered = eligibleTargetCandidates.stream()
                    .filter(v -> !v.getId().equals(excludeVocabId))
                    .toList();
            if (!filtered.isEmpty()) {
                targetPool = filtered;
            }
        }

        Vocabulary target = targetPool.get(ThreadLocalRandom.current().nextInt(targetPool.size()));

        // 2. Tìm 3 đáp án nhiễu khác biệt
        List<Vocabulary> distractors = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        // Đăng ký khóa của đáp án đúng để chống trùng lặp
        String targetMeaningKey = getMeaningKey(target.getMeaningVi());
        String targetJapaneseKey = getJapaneseKey(target.getJapaneseText());
        usedKeys.add(targetMeaningKey);
        usedKeys.add(targetJapaneseKey);

        // Quét lần 1: Tìm đáp án nhiễu trong cùng chủ đề
        List<Vocabulary> shuffledTopic = new ArrayList<>(topicVocabs);
        Collections.shuffle(shuffledTopic);
        for (Vocabulary v : shuffledTopic) {
            if (tryAddDistractor(v, target, safeDirection, distractors, usedKeys)) {
                if (distractors.size() == 3) {
                    break;
                }
            }
        }

        // Quét lần 2: Nếu chưa đủ 3 đáp án nhiễu, bổ sung từ toàn bộ từ vựng hệ thống
        if (distractors.size() < 3) {
            List<Vocabulary> allVocabs = new ArrayList<>(vocabularyRepository.findAll());
            Collections.shuffle(allVocabs);
            for (Vocabulary v : allVocabs) {
                if (tryAddDistractor(v, target, safeDirection, distractors, usedKeys)) {
                    if (distractors.size() == 3) {
                        break;
                    }
                }
            }
        }

        // Nếu vẫn không đủ 3 đáp án nhiễu hợp lệ (tổng cộng không đủ 4 lựa chọn khác nhau)
        if (distractors.size() < 3) {
            return Optional.empty();
        }

        // 3. Đóng gói 4 lựa chọn (1 đúng + 3 nhiễu) và xáo trộn ngẫu nhiên
        List<PracticeChoiceOptionDto> options = new ArrayList<>();
        options.add(buildChoiceOptionDto(target, safeDirection));
        for (Vocabulary d : distractors) {
            options.add(buildChoiceOptionDto(d, safeDirection));
        }
        Collections.shuffle(options);

        // 4. Xây dựng DTO câu hỏi gửi ra ngoài (không chứa cờ đúng/sai)
        String prompt;
        if ("VI_JA".equals(safeDirection)) {
            prompt = target.getMeaningVi();
        } else {
            prompt = target.getJapaneseText();
        }

        String baseJp = FuriganaHelper.extractBaseText(target.getJapaneseText());
        String speech = SpeechHelper.resolveSpeechText(target.getJapaneseText(), target.getReading());

        return Optional.of(PracticeChoiceQuestionDto.builder()
                .vocabId(target.getId())
                .questionPrompt(prompt)
                .baseJapaneseText(baseJp)
                .reading(target.getReading())
                .speechText(speech)
                .direction(safeDirection)
                .topicId(topicId)
                .topicName(target.getTopic() != null ? target.getTopic().getName() : "Tất cả chủ đề")
                .options(options)
                .build());
    }

    /**
     * Chấm câu trả lời trắc nghiệm phía máy chủ.
     * Chặn choiceVocabId không nằm trong bộ lựa chọn đã sinh của câu hỏi hiện tại.
     */
    public PracticeChoiceResultDto checkChoiceAnswer(PracticeChoiceAnswerForm form, List<Long> validOptionIds) {
        if (form.getVocabId() == null || form.getChoiceVocabId() == null) {
            throw new IllegalArgumentException("Dữ liệu câu trả lời không đầy đủ.");
        }

        if (validOptionIds == null || !validOptionIds.contains(form.getChoiceVocabId())) {
            throw new IllegalArgumentException("Lựa chọn không hợp lệ hoặc câu hỏi đã hết hạn.");
        }

        Vocabulary target = vocabularyRepository.findById(form.getVocabId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu hỏi với mã: " + form.getVocabId()));

        Vocabulary selected = vocabularyRepository.findById(form.getChoiceVocabId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phương án với mã: " + form.getChoiceVocabId()));

        String safeDirection = form.getSafeDirection();
        boolean correct = form.getVocabId().equals(form.getChoiceVocabId());

        String selectedAnswerText;
        String correctAnswerText;
        if ("VI_JA".equals(safeDirection)) {
            selectedAnswerText = selected.getJapaneseText() + (selected.getReading() != null && !selected.getReading().isBlank() ? " (" + selected.getReading().strip() + ")" : "");
            correctAnswerText = target.getJapaneseText() + (target.getReading() != null && !target.getReading().isBlank() ? " (" + target.getReading().strip() + ")" : "");
        } else {
            selectedAnswerText = selected.getMeaningVi();
            correctAnswerText = target.getMeaningVi();
        }

        String feedbackMessage;
        if (correct) {
            feedbackMessage = "Chính xác! Bạn đã chọn đáp án đúng.";
        } else {
            feedbackMessage = "Chưa chính xác! Bạn đã chọn: " + selectedAnswerText + ". Đáp án đúng là: " + correctAnswerText + ".";
        }

        String baseJp = FuriganaHelper.extractBaseText(target.getJapaneseText());
        String speech = SpeechHelper.resolveSpeechText(target.getJapaneseText(), target.getReading());

        return PracticeChoiceResultDto.builder()
                .vocabId(target.getId())
                .selectedVocabId(selected.getId())
                .correct(correct)
                .direction(safeDirection)
                .topicId(form.getTopicId())
                .topicName(target.getTopic() != null ? target.getTopic().getName() : "Tất cả chủ đề")
                .questionPrompt("VI_JA".equals(safeDirection) ? target.getMeaningVi() : target.getJapaneseText())
                .selectedAnswerText(selectedAnswerText)
                .correctAnswerText(correctAnswerText)
                .correctJapaneseText(target.getJapaneseText())
                .baseJapaneseText(baseJp)
                .correctReading(target.getReading())
                .correctMeaningVi(target.getMeaningVi())
                .correctRomaji(target.getRomaji())
                .speechText(speech)
                .feedbackMessage(feedbackMessage)
                .build();
    }

    private boolean isEligibleForChoice(Vocabulary v) {
        return v != null
                && v.getId() != null
                && v.getJapaneseText() != null && !v.getJapaneseText().isBlank()
                && v.getMeaningVi() != null && !v.getMeaningVi().isBlank();
    }

    private boolean tryAddDistractor(Vocabulary candidate, Vocabulary target, String direction,
                                     List<Vocabulary> distractors, Set<String> usedKeys) {
        if (!isEligibleForChoice(candidate)) {
            return false;
        }

        if (candidate.getId().equals(target.getId())) {
            return false;
        }

        String candidateMeaningKey = getMeaningKey(candidate.getMeaningVi());
        String candidateJpKey = getJapaneseKey(candidate.getJapaneseText());

        // Kiểm tra trùng nghĩa (hai từ khác nhau nhưng cùng nghĩa không được làm đáp án nhiễu của nhau)
        if (usedKeys.contains(candidateMeaningKey)) {
            return false;
        }

        // Kiểm tra trùng tiếng Nhật
        if (usedKeys.contains(candidateJpKey)) {
            return false;
        }

        // Đảm bảo không trùng các nghĩa thành phần với đáp án đúng
        List<String> targetMeanings = parseAcceptedMeanings(target.getMeaningVi());
        List<String> candidateMeanings = parseAcceptedMeanings(candidate.getMeaningVi());
        for (String tm : targetMeanings) {
            String tmKey = getMeaningKey(tm);
            for (String cm : candidateMeanings) {
                if (tmKey.equals(getMeaningKey(cm))) {
                    return false;
                }
            }
        }

        // Thêm vào danh sách đáp án nhiễu hợp lệ
        distractors.add(candidate);
        usedKeys.add(candidateMeaningKey);
        usedKeys.add(candidateJpKey);
        return true;
    }

    private PracticeChoiceOptionDto buildChoiceOptionDto(Vocabulary v, String direction) {
        String baseJp = FuriganaHelper.extractBaseText(v.getJapaneseText());
        String speech = SpeechHelper.resolveSpeechText(v.getJapaneseText(), v.getReading());

        String display;
        if ("VI_JA".equals(direction)) {
            display = v.getJapaneseText();
        } else {
            display = v.getMeaningVi();
        }

        return PracticeChoiceOptionDto.builder()
                .vocabId(v.getId())
                .displayText(display)
                .baseJapaneseText(baseJp)
                .reading(v.getReading())
                .romaji(v.getRomaji())
                .speechText(speech)
                .build();
    }

    private String getMeaningKey(String meaning) {
        if (meaning == null) {
            return "";
        }
        return normalizeMeaningInput(meaning).replaceAll("[\\s,;:.!?-]+", "").toLowerCase(Locale.ROOT);
    }

    private String getJapaneseKey(String jp) {
        if (jp == null) {
            return "";
        }
        String base = FuriganaHelper.extractBaseText(jp);
        return normalizeJapaneseInput(base != null ? base : jp).replaceAll("\\s+", "");
    }
}

