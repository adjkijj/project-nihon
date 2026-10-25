package com.nihon.service;

import com.nihon.dto.TopicForm;
import com.nihon.entity.Topic;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final KanjiEntryRepository kanjiEntryRepository;

    public TopicService(TopicRepository topicRepository,
                        VocabularyRepository vocabularyRepository,
                        KanjiEntryRepository kanjiEntryRepository) {
        this.topicRepository = topicRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<Topic> findAllOrdered() {
        return topicRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Topic> findRootTopics() {
        return topicRepository.findByParentIsNullOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Topic> findById(Long id) {
        if (id == null) return Optional.empty();
        return topicRepository.findById(id);
    }

    public Topic create(TopicForm form) {
        Topic t = new Topic();
        t.setName(form.getName().trim());

        if (form.getParentId() != null) {
            Topic parent = topicRepository.findById(form.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Chủ đề cha không tồn tại với ID: " + form.getParentId()));
            t.setParent(parent);
            t.setKanjiSection(parent.isKanjiSection());
        } else {
            t.setParent(null);
            t.setKanjiSection(form.isKanjiSection());
        }

        t.setSortOrder((int) topicRepository.count() + 1);
        t.setSourceLine(null);

        return topicRepository.save(t);
    }

    public Topic update(Long id, TopicForm form) {
        Topic t = topicRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với ID: " + id));

        t.setName(form.getName().trim());

        if (form.getParentId() != null) {
            if (form.getParentId().equals(id)) {
                throw new IllegalArgumentException("Chủ đề không thể chọn chính mình làm chủ đề cha.");
            }
            Topic parent = topicRepository.findById(form.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Chủ đề cha không tồn tại với ID: " + form.getParentId()));
            t.setParent(parent);
            t.setKanjiSection(parent.isKanjiSection());
        } else {
            t.setParent(null);
            t.setKanjiSection(form.isKanjiSection());
        }

        return topicRepository.save(t);
    }

    @Transactional(readOnly = true)
    public boolean canDelete(Long id) {
        if (id == null || !topicRepository.existsById(id)) {
            return false;
        }
        return !vocabularyRepository.existsByTopicId(id)
                && !topicRepository.existsByParentId(id)
                && !kanjiEntryRepository.existsByTopicId(id);
    }

    public void delete(Long id) {
        Topic t = topicRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với ID: " + id));

        if (vocabularyRepository.existsByTopicId(id)) {
            throw new IllegalStateException("Không thể xóa chủ đề vì đang có từ vựng thuộc chủ đề này.");
        }
        if (topicRepository.existsByParentId(id)) {
            throw new IllegalStateException("Không thể xóa chủ đề vì đang có các chủ đề con trực thuộc.");
        }
        if (kanjiEntryRepository.existsByTopicId(id)) {
            throw new IllegalStateException("Không thể xóa chủ đề vì đang có ký tự Kanji thuộc chủ đề này.");
        }

        topicRepository.delete(t);
    }

    public TopicForm toForm(Topic t) {
        TopicForm form = new TopicForm();
        form.setId(t.getId());
        form.setName(t.getName());
        form.setParentId(t.getParent() == null ? null : t.getParent().getId());
        form.setKanjiSection(t.isKanjiSection());
        return form;
    }
}
