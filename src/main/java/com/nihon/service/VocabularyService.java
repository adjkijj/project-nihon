package com.nihon.service;

import com.nihon.dto.VocabularyDeduplicationKey;
import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public Page<VocabularyRowDto> search(Long topicId, String keyword, Pageable pageable) {
        // Tải toàn bộ từ vựng kèm Topic trong 1 câu SQL duy nhất (JOIN FETCH) để chống N+1
        List<Vocabulary> allVocabs = vocabularyRepository.findAllWithTopic();

        // Nhóm các từ vựng theo khóa định danh chuẩn hóa (Japanese + Reading + MeaningVi)
        Map<VocabularyDeduplicationKey, List<Vocabulary>> grouped = new LinkedHashMap<>();
        for (Vocabulary v : allVocabs) {
            VocabularyDeduplicationKey key = VocabularyDeduplicationKey.of(v);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<VocabularyRowDto> rows = new ArrayList<>();

        for (Map.Entry<VocabularyDeduplicationKey, List<Vocabulary>> entry : grouped.entrySet()) {
            List<Vocabulary> list = entry.getValue();

            // Nếu lọc theo topicId: từ phải xuất hiện trong topicId đó
            Vocabulary matchingInTopic = null;
            if (topicId != null) {
                for (Vocabulary v : list) {
                    if (v.getTopic() != null && topicId.equals(v.getTopic().getId())) {
                        matchingInTopic = v;
                        break;
                    }
                }
                if (matchingInTopic == null) {
                    continue;
                }
            }

            // Nếu tìm kiếm theo keyword: kiểm tra tiếng Nhật, cách đọc, nghĩa tiếng Việt, romaji
            if (cleanKeyword != null) {
                boolean matched = list.stream().anyMatch(v -> matchesKeyword(v, cleanKeyword));
                if (!matched) {
                    continue;
                }
            }

            // Tổng hợp tất cả các chủ đề mà từ vựng này xuất hiện, sắp xếp theo sortOrder của chủ đề
            List<Topic> topics = list.stream()
                    .map(Vocabulary::getTopic)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Topic::getId, t -> t, (existing, replacement) -> existing, LinkedHashMap::new))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(Topic::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            List<Long> allIds = list.stream().map(Vocabulary::getId).toList();

            // Đại diện: nếu đang lọc theo topicId, chọn bản ghi thuộc topicId đó để nút Sửa/Xóa khớp với ngữ cảnh chủ đề đang chọn
            Vocabulary rep = (matchingInTopic != null) ? matchingInTopic : list.get(0);

            rows.add(VocabularyRowDto.builder()
                    .id(rep.getId())
                    .japaneseText(rep.getJapaneseText())
                    .reading(rep.getReading())
                    .romaji(rep.getRomaji())
                    .meaningVi(rep.getMeaningVi())
                    .hasKanji(rep.isHasKanji())
                    .topics(topics)
                    .allIds(allIds)
                    .build());
        }

        int total = rows.size();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int fromIndex = page * size;
        List<VocabularyRowDto> pageContent;
        if (fromIndex >= total) {
            pageContent = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + size, total);
            pageContent = rows.subList(fromIndex, toIndex);
        }

        return new PageImpl<>(pageContent, pageable, total);
    }

    private boolean matchesKeyword(Vocabulary v, String keyword) {
        if (v.getJapaneseText() != null && v.getJapaneseText().toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        if (v.getReading() != null && v.getReading().toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        if (v.getMeaningVi() != null && v.getMeaningVi().toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        if (v.getRomaji() != null && v.getRomaji().toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        return false;
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
