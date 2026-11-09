package com.nihon.service;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.dto.KanjiForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.validation.TextNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class KanjiService {

    private static final int MAX_KEYWORD_LENGTH = 50;

    private final KanjiEntryRepository kanjiEntryRepository;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;

    public KanjiService(KanjiEntryRepository kanjiEntryRepository,
                        TopicRepository topicRepository,
                        VocabularyRepository vocabularyRepository) {
        this.kanjiEntryRepository = kanjiEntryRepository;
        this.topicRepository = topicRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    /**
     * Tìm kiếm và lọc Kanji theo chủ đề và từ khóa (ký tự Kanji, Hán Việt, ý nghĩa, âm On/Kun).
     */
    public Page<KanjiDetailDto> search(Long topicId, String keyword, Pageable pageable) {
        String sanitizedKeyword = sanitizeKeyword(keyword);
        Page<KanjiEntry> page = kanjiEntryRepository.search(topicId, sanitizedKeyword, pageable);
        return page.map(entity -> {
            KanjiDetailDto dto = KanjiDetailDto.fromEntity(entity);
            Long tId = entity.getTopic() != null ? entity.getTopic().getId() : null;
            dto.setWords(getWordsForKanji(tId, entity.getCharacter()));
            return dto;
        });
    }

    /**
     * Lấy chi tiết một Kanji theo ID.
     */
    public Optional<KanjiDetailDto> findDetailById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return kanjiEntryRepository.findById(id).map(entity -> {
            KanjiDetailDto dto = KanjiDetailDto.fromEntity(entity);
            Long tId = entity.getTopic() != null ? entity.getTopic().getId() : null;
            dto.setWords(getWordsForKanji(tId, entity.getCharacter()));
            return dto;
        });
    }

    /**
     * Lấy danh sách từ vựng cần nhớ của một ký tự Kanji từ bảng từ vựng chung.
     */
    public List<com.nihon.dto.KanjiWordSummaryDto> getWordsForKanji(Long topicId, String character) {
        if (character == null || character.isBlank()) {
            return Collections.emptyList();
        }
        String cleanChar = character.strip();
        List<Vocabulary> list;
        if (topicId != null) {
            list = vocabularyRepository.findWordsByTopicIdAndCharacter(topicId, cleanChar);
            if (list.isEmpty()) {
                list = vocabularyRepository.findWordsByCharacter(cleanChar);
            }
        } else {
            list = vocabularyRepository.findWordsByCharacter(cleanChar);
        }
        return list.stream()
                .map(v -> com.nihon.dto.KanjiWordSummaryDto.builder()
                        .japaneseText(v.getJapaneseText())
                        .reading(v.getReading())
                        .romaji(v.getRomaji())
                        .meaningVi(v.getMeaningVi())
                        .build())
                .toList();
    }

    /**
     * Lấy danh sách các chủ đề thuộc khu vực Kanji để hiển thị trong bộ lọc.
     */
    public List<Topic> getKanjiTopics() {
        return topicRepository.findByKanjiSectionTrueOrderBySortOrderAsc();
    }

    /**
     * Kiểm tra chủ đề có tồn tại hay không.
     */
    public boolean existsTopicById(Long topicId) {
        if (topicId == null || topicId <= 0) {
            return false;
        }
        return topicRepository.existsById(topicId);
    }

    /**
     * Lấy danh sách từ vựng có chứa ký tự Kanji này từ bảng từ vựng chung để làm ví dụ thực tế.
     */
    public List<Vocabulary> getRelatedVocabulary(String character) {
        return getRelatedVocabulary(character, null);
    }

    /**
     * Lấy danh sách từ vựng có chứa ký tự Kanji này theo chủ đề bài học và ký tự.
     */
    public List<Vocabulary> getRelatedVocabulary(String character, Long topicId) {
        if (character == null || character.isBlank()) {
            return Collections.emptyList();
        }
        String cleanChar = character.strip();
        if (topicId != null) {
            List<Vocabulary> topicWords = vocabularyRepository.findWordsByTopicIdAndCharacter(topicId, cleanChar);
            if (!topicWords.isEmpty()) {
                return topicWords;
            }
        }
        return vocabularyRepository.findTop10ByJapaneseTextContainingOrderBySourceLineAsc(cleanChar);
    }

    /**
     * Đếm tổng số Kanji trong hệ thống.
     */
    public long countTotal() {
        return kanjiEntryRepository.count();
    }

    /**
     * Tìm thực thể KanjiEntry theo ID.
     */
    public Optional<KanjiEntry> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return kanjiEntryRepository.findById(id);
    }

    /**
     * Tạo mới một bản ghi Kanji từ form.
     * Kiểm tra chủ đề thuộc nhánh Kanji (kanjiSection = true), kiểm tra không trùng character,
     * chuẩn hóa văn bản bằng TextNormalizer và gán sourceLine = findMaxSourceLine() + 1.
     */
    @Transactional
    public KanjiEntry create(KanjiForm form) {
        Topic topic = validateAndGetKanjiTopic(form.getTopicId());

        String normalizedChar = TextNormalizer.normalize(form.getCharacter());
        if (normalizedChar == null || normalizedChar.codePointCount(0, normalizedChar.length()) != 1) {
            throw new IllegalArgumentException("Phải là đúng một ký tự Kanji");
        }
        if (kanjiEntryRepository.existsByCharacter(normalizedChar)) {
            throw new IllegalArgumentException("Kanji này đã tồn tại.");
        }

        KanjiEntry entry = new KanjiEntry();
        entry.setTopic(topic);
        entry.setCharacter(normalizedChar);
        entry.setHanViet(TextNormalizer.normalize(form.getHanViet()));
        entry.setMeaningVi(TextNormalizer.normalize(form.getMeaningVi()));
        entry.setOnyomi(TextNormalizer.normalize(form.getOnyomi()));
        entry.setKunyomi(TextNormalizer.normalize(form.getKunyomi()));

        Integer maxSourceLine = kanjiEntryRepository.findMaxSourceLine();
        entry.setSourceLine((maxSourceLine != null ? maxSourceLine : 0) + 1);

        return kanjiEntryRepository.save(entry);
    }

    /**
     * Cập nhật một bản ghi Kanji theo ID.
     * Kiểm tra chủ đề thuộc nhánh Kanji (kanjiSection = true) và kiểm tra không trùng character
     * (bỏ qua chính bản ghi đang sửa).
     */
    @Transactional
    public KanjiEntry update(Long id, KanjiForm form) {
        KanjiEntry entry = kanjiEntryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy Kanji với ID: " + id));

        Topic topic = validateAndGetKanjiTopic(form.getTopicId());

        String normalizedChar = TextNormalizer.normalize(form.getCharacter());
        if (normalizedChar == null || normalizedChar.codePointCount(0, normalizedChar.length()) != 1) {
            throw new IllegalArgumentException("Phải là đúng một ký tự Kanji");
        }
        if (kanjiEntryRepository.existsByCharacterAndIdNot(normalizedChar, id)) {
            throw new IllegalArgumentException("Kanji này đã tồn tại.");
        }

        entry.setTopic(topic);
        entry.setCharacter(normalizedChar);
        entry.setHanViet(TextNormalizer.normalize(form.getHanViet()));
        entry.setMeaningVi(TextNormalizer.normalize(form.getMeaningVi()));
        entry.setOnyomi(TextNormalizer.normalize(form.getOnyomi()));
        entry.setKunyomi(TextNormalizer.normalize(form.getKunyomi()));

        return kanjiEntryRepository.save(entry);
    }

    /**
     * Xóa một bản ghi Kanji theo ID (không xóa các từ vựng trong bảng vocabulary).
     */
    @Transactional
    public void delete(Long id) {
        KanjiEntry entry = kanjiEntryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy Kanji với ID: " + id));
        kanjiEntryRepository.delete(entry);
    }

    /**
     * Chuyển đổi thực thể KanjiEntry sang KanjiForm để hiển thị trên form sửa.
     */
    public KanjiForm toForm(KanjiEntry entry) {
        KanjiForm form = new KanjiForm();
        form.setId(entry.getId());
        if (entry.getTopic() != null) {
            form.setTopicId(entry.getTopic().getId());
        }
        form.setCharacter(entry.getCharacter());
        form.setHanViet(entry.getHanViet());
        form.setMeaningVi(entry.getMeaningVi());
        form.setOnyomi(entry.getOnyomi());
        form.setKunyomi(entry.getKunyomi());
        return form;
    }

    private Topic validateAndGetKanjiTopic(Long topicId) {
        if (topicId == null || topicId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn chủ đề hợp lệ.");
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + topicId));
        if (!topic.isKanjiSection()) {
            throw new IllegalArgumentException("Chủ đề đã chọn không thuộc nhánh Kanji.");
        }
        return topic;
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed;
    }
}
