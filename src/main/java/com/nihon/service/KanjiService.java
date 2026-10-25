package com.nihon.service;

import com.nihon.dto.KanjiDetailDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
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
        return page.map(KanjiDetailDto::fromEntity);
    }

    /**
     * Lấy chi tiết một Kanji theo ID.
     */
    public Optional<KanjiDetailDto> findDetailById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return kanjiEntryRepository.findById(id).map(KanjiDetailDto::fromEntity);
    }

    /**
     * Lấy danh sách các chủ đề thuộc khu vực Kanji để hiển thị trong bộ lọc.
     */
    public List<Topic> getKanjiTopics() {
        return topicRepository.findByKanjiSectionTrueOrderBySortOrderAsc();
    }

    /**
     * Lấy danh sách tối đa 10 từ vựng có chứa ký tự Kanji này để làm ví dụ thực tế.
     */
    public List<Vocabulary> getRelatedVocabulary(String character) {
        if (character == null || character.isBlank()) {
            return Collections.emptyList();
        }
        return vocabularyRepository.findTop10ByJapaneseTextContainingOrderBySourceLineAsc(character.strip());
    }

    /**
     * Đếm tổng số Kanji trong hệ thống.
     */
    public long countTotal() {
        return kanjiEntryRepository.count();
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
