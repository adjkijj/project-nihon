package com.nihon.service;

import com.nihon.conjugation.ConjugatedForms;
import com.nihon.conjugation.MasuInferenceResult;
import com.nihon.conjugation.VerbConjugator;
import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VerbRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
}
