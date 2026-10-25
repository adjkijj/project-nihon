package com.nihon.service;

import com.nihon.dto.VocabularyForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final TopicRepository topicRepository;

    public VocabularyService(VocabularyRepository vocabularyRepository, TopicRepository topicRepository) {
        this.vocabularyRepository = vocabularyRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public Page<Vocabulary> search(Long topicId, String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return vocabularyRepository.search(topicId, cleanKeyword, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Vocabulary> findById(Long id) {
        if (id == null) return Optional.empty();
        return vocabularyRepository.findById(id);
    }

    public Vocabulary create(VocabularyForm form) {
        Topic topic = topicRepository.findById(form.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + form.getTopicId()));

        Vocabulary v = new Vocabulary();
        v.setTopic(topic);
        v.setJapaneseText(form.getJapaneseText().trim());
        v.setReading(normalizeReading(form.getReading()));
        v.setRomaji(form.getRomaji().trim());
        v.setMeaningVi(form.getMeaningVi().trim());
        v.setHasKanji(VocabularyFileParser.containsKanji(v.getJapaneseText()));
        v.setSourceLine(null);

        return vocabularyRepository.save(v);
    }

    public Vocabulary update(Long id, VocabularyForm form) {
        Vocabulary v = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));

        Topic topic = topicRepository.findById(form.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + form.getTopicId()));

        v.setTopic(topic);
        v.setJapaneseText(form.getJapaneseText().trim());
        v.setReading(normalizeReading(form.getReading()));
        v.setRomaji(form.getRomaji().trim());
        v.setMeaningVi(form.getMeaningVi().trim());
        v.setHasKanji(VocabularyFileParser.containsKanji(v.getJapaneseText()));

        return vocabularyRepository.save(v);
    }

    public void delete(Long id) {
        Vocabulary v = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));
        vocabularyRepository.delete(v);
    }

    public VocabularyForm toForm(Vocabulary v) {
        VocabularyForm form = new VocabularyForm();
        form.setId(v.getId());
        form.setTopicId(v.getTopic().getId());
        form.setJapaneseText(v.getJapaneseText());
        form.setReading(v.getReading());
        form.setRomaji(v.getRomaji());
        form.setMeaningVi(v.getMeaningVi());
        return form;
    }

    private String normalizeReading(String reading) {
        if (reading == null || reading.isBlank()) {
            return null;
        }
        return reading.trim();
    }
}
