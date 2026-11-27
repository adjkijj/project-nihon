package com.nihon.service;

import com.nihon.conjugation.ConjugatedForms;
import com.nihon.conjugation.MasuInferenceResult;
import com.nihon.conjugation.TargetForm;
import com.nihon.conjugation.VerbConjugator;
import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.dto.verb.VerbLabResultDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VerbRepository;
import com.nihon.validation.TextNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class VerbService {

    public static final String CONSISTENCY_ERROR_MESSAGE = "Thể ます và thể từ điển không khớp với nhóm đã chọn";
    public static final int MAX_BULK_LINES = 100;

    private final VerbRepository verbRepository;
    private final TopicRepository topicRepository;

    public VerbService(VerbRepository verbRepository, TopicRepository topicRepository) {
        this.verbRepository = verbRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public Page<Verb> search(Integer verbGroup, Long topicId, String keyword, Pageable pageable) {
        String safeKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Integer safeGroup = (verbGroup != null && verbGroup >= 1 && verbGroup <= 3) ? verbGroup : null;
        Long safeTopicId = (topicId != null && topicId > 0) ? topicId : null;
        return verbRepository.search(safeGroup, safeTopicId, safeKeyword, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Verb> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return verbRepository.findByIdWithTopic(id);
    }

    @Transactional(readOnly = true)
    public Verb getById(Long id) {
        return findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy động từ với ID: " + id));
    }

    @Transactional(readOnly = true)
    public VerbDetailDto getDetailById(Long id) {
        Verb verb = getById(id);
        ConjugatedForms forms = VerbConjugator.conjugate(
                verb.getDictionaryForm(),
                verb.getVerbGroup(),
                verb.getKanjiText()
        );
        return VerbDetailDto.fromEntity(verb, forms);
    }

    public Verb create(VerbForm form) {
        validateForm(form, null);

        Topic topic = null;
        if (form.getTopicId() != null && form.getTopicId() > 0) {
            topic = topicRepository.findById(form.getTopicId()).orElse(null);
        }

        String kanjiText = normalizeKanjiText(form.getKanjiText());

        Verb verb = Verb.builder()
                .dictionaryForm(form.getDictionaryForm().trim())
                .masuForm(form.getMasuForm().trim())
                .kanjiText(kanjiText)
                .verbGroup(form.getVerbGroup())
                .meaningVi(form.getMeaningVi().trim())
                .topic(topic)
                .build();

        return verbRepository.save(verb);
    }

    public Verb update(Long id, VerbForm form) {
        Verb existing = getById(id);
        validateForm(form, id);

        Topic topic = null;
        if (form.getTopicId() != null && form.getTopicId() > 0) {
            topic = topicRepository.findById(form.getTopicId()).orElse(null);
        }

        existing.setDictionaryForm(form.getDictionaryForm().trim());
        existing.setMasuForm(form.getMasuForm().trim());
        existing.setKanjiText(normalizeKanjiText(form.getKanjiText()));
        existing.setVerbGroup(form.getVerbGroup());
        existing.setMeaningVi(form.getMeaningVi().trim());
        existing.setTopic(topic);

        return verbRepository.save(existing);
    }

    public void delete(Long id) {
        Verb existing = getById(id);
        verbRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public long count() {
        return verbRepository.count();
    }

    public List<VerbBulkResultItem> bulkAdd(String text, Long topicId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Dữ liệu hàng loạt không được để trống.");
        }

        List<String> rawLines = text.lines().toList();
        if (rawLines.size() > MAX_BULK_LINES) {
            throw new IllegalArgumentException("Dữ liệu hàng loạt không được vượt quá " + MAX_BULK_LINES + " dòng.");
        }

        Topic topic = null;
        if (topicId != null && topicId > 0) {
            topic = topicRepository.findById(topicId).orElse(null);
        }

        List<VerbBulkResultItem> results = new ArrayList<>();
        int lineNumber = 0;

        for (String rawLine : rawLines) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(",", 2);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                results.add(VerbBulkResultItem.builder()
                        .lineNumber(lineNumber)
                        .line(line)
                        .success(false)
                        .message("Định dạng không hợp lệ, yêu cầu: 'ひらがなます,nghĩa'")
                        .build());
                continue;
            }

            String masuForm = parts[0].trim();
            String meaningVi = parts[1].trim();

            MasuInferenceResult inference = VerbConjugator.inferFromMasu(masuForm);
            if (!inference.isValid()) {
                results.add(VerbBulkResultItem.builder()
                        .lineNumber(lineNumber)
                        .line(line)
                        .success(false)
                        .message(inference.getErrorMessage() != null ? inference.getErrorMessage() : "Thể ます không hợp lệ")
                        .build());
                continue;
            }

            if (inference.isAmbiguous()) {
                results.add(VerbBulkResultItem.builder()
                        .lineNumber(lineNumber)
                        .line(line)
                        .success(false)
                        .message("Không thể tự động xác định nhóm động từ cho '" + masuForm + "' (hàng i). Vui lòng thêm thủ công.")
                        .build());
                continue;
            }

            String dict = inference.getDictionaryForm();
            Integer group = inference.getVerbGroup();

            if (verbRepository.existsByDictionaryFormAndKanjiText(dict, null)) {
                results.add(VerbBulkResultItem.builder()
                        .lineNumber(lineNumber)
                        .line(line)
                        .success(false)
                        .message("Động từ '" + dict + "' đã tồn tại trong hệ thống")
                        .build());
                continue;
            }

            Verb verb = Verb.builder()
                    .dictionaryForm(dict)
                    .masuForm(masuForm)
                    .kanjiText(null)
                    .verbGroup(group)
                    .meaningVi(meaningVi)
                    .topic(topic)
                    .build();

            Verb saved = verbRepository.save(verb);
            results.add(VerbBulkResultItem.builder()
                    .lineNumber(lineNumber)
                    .line(line)
                    .success(true)
                    .message("Thành công: Nhóm " + group + " (" + dict + ")")
                    .verb(saved)
                    .build());
        }

        return results;
    }

    public List<Verb> loadSampleData() {
        if (verbRepository.count() > 0) {
            throw new IllegalStateException("Chỉ có thể nạp dữ liệu mẫu khi danh sách động từ đang trống.");
        }

        List<Verb> sampleVerbs = List.of(
                Verb.builder()
                        .dictionaryForm("のむ")
                        .kanjiText("飲む")
                        .masuForm("のみます")
                        .verbGroup(1)
                        .meaningVi("Uống (Dữ liệu mẫu)")
                        .build(),
                Verb.builder()
                        .dictionaryForm("たべる")
                        .kanjiText("食べる")
                        .masuForm("たべます")
                        .verbGroup(2)
                        .meaningVi("Ăn (Dữ liệu mẫu)")
                        .build(),
                Verb.builder()
                        .dictionaryForm("いく")
                        .kanjiText("行く")
                        .masuForm("いきます")
                        .verbGroup(1)
                        .meaningVi("Đi (Dữ liệu mẫu)")
                        .build(),
                Verb.builder()
                        .dictionaryForm("くる")
                        .kanjiText("来る")
                        .masuForm("きます")
                        .verbGroup(3)
                        .meaningVi("Đến (Dữ liệu mẫu)")
                        .build(),
                Verb.builder()
                        .dictionaryForm("べんきょうする")
                        .kanjiText("勉強する")
                        .masuForm("べんきょうします")
                        .verbGroup(3)
                        .meaningVi("Học (Dữ liệu mẫu)")
                        .build()
        );

        return verbRepository.saveAll(sampleVerbs);
    }

    private void validateForm(VerbForm form, Long excludeId) {
        String dict = form.getDictionaryForm() != null ? form.getDictionaryForm().trim() : "";
        String masu = form.getMasuForm() != null ? form.getMasuForm().trim() : "";
        Integer group = form.getVerbGroup();
        String kanji = normalizeKanjiText(form.getKanjiText());

        boolean consistent = VerbConjugator.isConsistent(dict, masu, group, kanji);
        if (!consistent) {
            throw new IllegalArgumentException(CONSISTENCY_ERROR_MESSAGE);
        }

        boolean exists;
        if (excludeId != null) {
            exists = verbRepository.existsByDictionaryFormAndKanjiTextAndIdNot(dict, kanji, excludeId);
        } else {
            exists = verbRepository.existsByDictionaryFormAndKanjiText(dict, kanji);
        }

        if (exists) {
            String label = dict + (kanji != null ? " (" + kanji + ")" : "");
            throw new IllegalArgumentException("Động từ '" + label + "' đã tồn tại trong hệ thống.");
        }
    }

    private String normalizeKanjiText(String kanjiText) {
        if (kanjiText == null || kanjiText.isBlank()) {
            return null;
        }
        return kanjiText.trim();
    }

    @Transactional(readOnly = true)
    public VerbLabResultDto processLabInput(String input, String selectedGroup) {
        if (input == null || input.isBlank()) {
            return VerbLabResultDto.builder().hasResult(false).build();
        }

        if (input.length() > 100) {
            return VerbLabResultDto.error(input, selectedGroup, "Độ dài từ nhập vào không được vượt quá 100 ký tự.");
        }

        String normalized = TextNormalizer.normalize(input.trim());

        // Kiểm tra ký tự tiếng Nhật hợp lệ
        boolean hasJapanese = normalized.codePoints().anyMatch(cp -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            return script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN;
        });

        if (!hasJapanese) {
            return VerbLabResultDto.error(normalized, selectedGroup, "Vui lòng nhập động từ tiếng Nhật hợp lệ bằng chữ Hiragana hoặc Kanji.");
        }

        String groupChoice = selectedGroup != null ? selectedGroup.trim().toUpperCase() : "AUTO";
        Integer group = null;
        if ("1".equals(groupChoice)) group = 1;
        else if ("2".equals(groupChoice)) group = 2;
        else if ("3".equals(groupChoice)) group = 3;

        boolean isMasu = normalized.endsWith("ます");
        String kanji = containsKanji(normalized) ? normalized : null;

        if (group != null) {
            String dictForm;
            if (isMasu) {
                dictForm = VerbConjugator.masuToDictionary(normalized, group);
                if (dictForm == null) {
                    return VerbLabResultDto.error(normalized, groupChoice, "Thể ます không phù hợp với Nhóm " + group + " đã chọn.");
                }
            } else {
                dictForm = normalized;
            }

            try {
                ConjugatedForms forms = VerbConjugator.conjugate(dictForm, group, kanji);
                return buildLabResult(normalized, groupChoice, group, forms, null);
            } catch (Exception e) {
                return VerbLabResultDto.error(normalized, groupChoice, "Không thể chia thể: " + e.getMessage());
            }
        }

        // Tự nhận biết nhóm (AUTO): Chỉ nhận biết với する / くる / します / きます, còn lại yêu cầu người dùng chọn nhóm
        if (normalized.endsWith("する") || normalized.endsWith("します")) {
            int g = 3;
            String dictForm = normalized.endsWith("します") ? normalized.substring(0, normalized.length() - 2) + "する" : normalized;
            ConjugatedForms forms = VerbConjugator.conjugate(dictForm, g, kanji);
            return buildLabResult(normalized, groupChoice, g, forms, "Tự động nhận biết: Động từ Nhóm 3 (kết thúc bằng する/します)");
        }

        if (normalized.endsWith("くる") || normalized.endsWith("来る") || normalized.endsWith("きます") || normalized.endsWith("来ます")) {
            int g = 3;
            String dictForm = normalized.contains("来") ? (normalized.endsWith("来る") ? normalized : "来る") : (normalized.endsWith("くる") ? normalized : "くる");
            ConjugatedForms forms = VerbConjugator.conjugate(dictForm, g, kanji);
            return buildLabResult(normalized, groupChoice, g, forms, "Tự động nhận biết: Động từ Nhóm 3 (kết thúc bằng くる/きます)");
        }

        return VerbLabResultDto.ambiguous(normalized, "AUTO",
                "Trường hợp này không thuộc diện tự nhận biết (chỉ hỗ trợ tự động cho động từ kết thúc bằng する hoặc くる). Vui lòng chọn cụ thể Nhóm 1, 2 hoặc 3.");
    }

    private VerbLabResultDto buildLabResult(String input, String selectedGroup, int group, ConjugatedForms forms, String explanation) {
        return VerbLabResultDto.builder()
                .input(input)
                .selectedGroup(selectedGroup)
                .inferredGroup(group)
                .dictionaryForm(forms.getDictionaryForm())
                .masuForm(forms.getMasuForm())
                .teForm(forms.getTeForm())
                .taForm(forms.getTaForm())
                .naiForm(forms.getNaiForm())
                .kanjiDictionaryForm(forms.getKanjiDictionaryForm())
                .kanjiMasuForm(forms.getKanjiMasuForm())
                .kanjiTeForm(forms.getKanjiTeForm())
                .kanjiTaForm(forms.getKanjiTaForm())
                .kanjiNaiForm(forms.getKanjiNaiForm())
                .kanjiText(forms.getKanjiDictionaryForm())
                .ambiguous(false)
                .hasResult(true)
                .explanation(explanation)
                .build();
    }

    private static boolean containsKanji(String text) {
        if (text == null) return false;
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    @Transactional(readOnly = true)
    public byte[] exportQuizletText(Long topicId, Integer verbGroup, TargetForm targetForm) {
        TargetForm actualForm = (targetForm != null && targetForm != TargetForm.RANDOM) ? targetForm : TargetForm.TE;
        Pageable pageable = PageRequest.of(0, 5000, Sort.by("id").ascending());
        Page<Verb> page = search(verbGroup, topicId, null, pageable);
        List<Verb> verbs = page.getContent();

        StringBuilder sb = new StringBuilder();
        for (Verb v : verbs) {
            ConjugatedForms forms = VerbConjugator.conjugate(v.getDictionaryForm(), v.getVerbGroup(), v.getKanjiText());
            String term = VocabularyExportService.cleanField(v.getMasuForm() + (v.getKanjiText() != null ? " (" + v.getKanjiText() + ")" : ""));
            String conjugatedKana = VerbConjugator.getFormKana(forms, actualForm);
            String conjugatedKanji = VerbConjugator.getFormKanji(forms, actualForm);
            String formDisplay = conjugatedKana + (conjugatedKanji != null && !conjugatedKanji.equals(conjugatedKana) ? " (" + conjugatedKanji + ")" : "");
            String def = VocabularyExportService.cleanField(formDisplay + " : " + (v.getMeaningVi() != null ? v.getMeaningVi() : ""));

            sb.append(term).append("\t").append(def).append("\r\n");
        }

        byte[] contentBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] utf8Bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[utf8Bom.length + contentBytes.length];
        System.arraycopy(utf8Bom, 0, result, 0, utf8Bom.length);
        System.arraycopy(contentBytes, 0, result, utf8Bom.length, contentBytes.length);
        return result;
    }

    @Transactional(readOnly = true)
    public List<VerbDetailDto> findByTopicIdWithForms(Long topicId) {
        if (topicId == null || topicId <= 0) {
            return Collections.emptyList();
        }
        List<Verb> list = verbRepository.findByTopicId(topicId);
        return list.stream()
                .map(v -> {
                    ConjugatedForms forms = VerbConjugator.conjugate(v.getDictionaryForm(), v.getVerbGroup(), v.getKanjiText());
                    return VerbDetailDto.fromEntity(v, forms);
                })
                .toList();
    }
}
