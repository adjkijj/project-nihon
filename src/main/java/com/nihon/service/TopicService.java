package com.nihon.service;

import com.nihon.dto.TopicDetailDto;
import com.nihon.dto.TopicForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.validation.TextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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
        if (id == null || id <= 0) return Optional.empty();
        return topicRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        if (id == null || id <= 0) return false;
        return topicRepository.existsById(id);
    }

    public Topic create(TopicForm form) {
        String normalizedName = TextNormalizer.normalize(form.getName());
        if (normalizedName == null) {
            throw new IllegalArgumentException("Tên chủ đề không được để trống.");
        }

        Topic t = new Topic();
        t.setName(normalizedName);

        Long parentId = form.getParentId();
        if (parentId != null) {
            Topic parent = topicRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Chủ đề cha không tồn tại với ID: " + parentId));
            if (parent.getParent() != null) {
                throw new IllegalArgumentException("Chủ đề cha phải là chủ đề gốc.");
            }
            t.setParent(parent);
            t.setKanjiSection(parent.isKanjiSection());
        } else {
            t.setParent(null);
            t.setKanjiSection(form.isKanjiSection());
        }

        if (topicRepository.existsDuplicateNameInParent(normalizedName, parentId, null)) {
            throw new IllegalArgumentException("Tên chủ đề đã tồn tại trong cùng nhóm.");
        }

        Integer maxSortOrder = topicRepository.findMaxSortOrder();
        t.setSortOrder((maxSortOrder != null ? maxSortOrder : 0) + 1);
        t.setSourceLine(null);

        return topicRepository.save(t);
    }

    public Topic update(Long id, TopicForm form) {
        Topic t = topicRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với ID: " + id));

        String normalizedName = TextNormalizer.normalize(form.getName());
        if (normalizedName == null) {
            throw new IllegalArgumentException("Tên chủ đề không được để trống.");
        }

        Long parentId = form.getParentId();
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new IllegalArgumentException("Chủ đề không thể chọn chính mình làm chủ đề cha.");
            }
            if (topicRepository.existsByParentId(id)) {
                throw new IllegalArgumentException("Chủ đề đang có chủ đề con nên không thể chuyển thành chủ đề con.");
            }
            Topic parent = topicRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Chủ đề cha không tồn tại với ID: " + parentId));
            if (parent.getParent() != null) {
                throw new IllegalArgumentException("Chủ đề cha phải là chủ đề gốc.");
            }
            t.setParent(parent);
            t.setKanjiSection(parent.isKanjiSection());
        } else {
            t.setParent(null);
            t.setKanjiSection(form.isKanjiSection());
        }

        if (topicRepository.existsDuplicateNameInParent(normalizedName, parentId, id)) {
            throw new IllegalArgumentException("Tên chủ đề đã tồn tại trong cùng nhóm.");
        }

        t.setName(normalizedName);
        Topic saved = topicRepository.save(t);

        // Nếu là chủ đề gốc, đồng bộ kanjiSection cho toàn bộ chủ đề con trong cùng transaction
        if (parentId == null) {
            List<Topic> children = topicRepository.findByParentIdOrderBySortOrderAsc(id);
            if (!children.isEmpty()) {
                for (Topic child : children) {
                    child.setKanjiSection(saved.isKanjiSection());
                }
                topicRepository.saveAll(children);
            }
        }

        return saved;
    }

    /**
     * Lấy thông tin chi tiết của một chủ đề theo ID.
     */
    @Transactional(readOnly = true)
    public Optional<TopicDetailDto> findDetailById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return topicRepository.findById(id).map(t -> {
            List<Topic> children = topicRepository.findByParentIdOrderBySortOrderAsc(id);
            long vocabCount = vocabularyRepository.countByTopicId(id);
            long kanjiCount = kanjiEntryRepository.countByTopicId(id);
            String reason = buildCannotDeleteReason(children.size(), vocabCount, kanjiCount);
            return TopicDetailDto.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .parentId(t.getParent() != null ? t.getParent().getId() : null)
                    .parentName(t.getParent() != null ? t.getParent().getName() : null)
                    .kanjiSection(t.isKanjiSection())
                    .sortOrder(t.getSortOrder())
                    .vocabularyCount(vocabCount)
                    .kanjiCount(kanjiCount)
                    .children(children)
                    .canDelete(reason == null)
                    .cannotDeleteReason(reason)
                    .build();
        });
    }

    /**
     * Trả về lý do không thể xóa chủ đề, hoặc null nếu chủ đề được phép xóa.
     */
    @Transactional(readOnly = true)
    public String getCannotDeleteReason(Long id) {
        if (id == null || id <= 0 || !topicRepository.existsById(id)) {
            return null;
        }
        long childCount = topicRepository.countByParentId(id);
        long vocabCount = vocabularyRepository.countByTopicId(id);
        long kanjiCount = kanjiEntryRepository.countByTopicId(id);
        return buildCannotDeleteReason(childCount, vocabCount, kanjiCount);
    }

    /**
     * Trả về bản đồ (topicId -> lý do không xóa được) cho danh sách chủ đề.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getCannotDeleteReasons(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        for (Topic t : topics) {
            if (t != null && t.getId() != null) {
                String reason = getCannotDeleteReason(t.getId());
                if (reason != null) {
                    map.put(t.getId(), reason);
                }
            }
        }
        return map;
    }

    private String buildCannotDeleteReason(long childCount, long vocabCount, long kanjiCount) {
        List<String> parts = new ArrayList<>();
        if (childCount > 0) {
            parts.add(childCount + " chủ đề con");
        }
        if (vocabCount > 0) {
            parts.add(vocabCount + " từ vựng");
        }
        if (kanjiCount > 0) {
            parts.add(kanjiCount + " Kanji");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "Không thể xóa vì đang có " + String.join(", ", parts);
    }

    /**
     * Đổi thứ tự sortOrder của chủ đề với chủ đề liền kề cùng cấp (cùng parent).
     * Direction chỉ nhận UP hoặc DOWN. Nếu ở đầu hoặc cuối danh sách thì bỏ qua, không báo lỗi.
     */
    public boolean move(Long id, String direction) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID không hợp lệ");
        }
        Topic target = topicRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề với ID: " + id));

        if (direction == null) {
            throw new IllegalArgumentException("Hướng di chuyển không hợp lệ (chỉ chấp nhận UP hoặc DOWN).");
        }
        String cleanDir = direction.strip();
        boolean isUp;
        if ("UP".equalsIgnoreCase(cleanDir)) {
            isUp = true;
        } else if ("DOWN".equalsIgnoreCase(cleanDir)) {
            isUp = false;
        } else {
            throw new IllegalArgumentException("Hướng di chuyển không hợp lệ (chỉ chấp nhận UP hoặc DOWN).");
        }

        List<Topic> siblings;
        if (target.getParent() == null) {
            siblings = new ArrayList<>(topicRepository.findByParentIsNullOrderBySortOrderAsc());
        } else {
            siblings = new ArrayList<>(topicRepository.findByParentIdOrderBySortOrderAsc(target.getParent().getId()));
        }

        siblings.sort(Comparator.comparing(Topic::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Topic::getId));

        // Chuẩn hóa nếu có giá trị sortOrder trùng nhau hoặc null trong cùng nhóm anh em
        boolean normalizedAny = false;
        for (int i = 1; i < siblings.size(); i++) {
            Integer prevOrder = siblings.get(i - 1).getSortOrder();
            Integer currOrder = siblings.get(i).getSortOrder();
            if (prevOrder == null) {
                siblings.get(i - 1).setSortOrder(i);
                prevOrder = i;
                normalizedAny = true;
            }
            if (currOrder == null || currOrder <= prevOrder) {
                siblings.get(i).setSortOrder(prevOrder + 1);
                normalizedAny = true;
            }
        }
        if (normalizedAny) {
            topicRepository.saveAll(siblings);
        }

        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (Objects.equals(siblings.get(i).getId(), id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return false;
        }

        int swapIdx = isUp ? (idx - 1) : (idx + 1);
        if (swapIdx < 0 || swapIdx >= siblings.size()) {
            // Ở đầu hoặc cuối danh sách cùng cấp: bỏ qua, không lỗi
            return false;
        }

        Topic current = siblings.get(idx);
        Topic neighbor = siblings.get(swapIdx);
        Integer tempOrder = current.getSortOrder();
        current.setSortOrder(neighbor.getSortOrder());
        neighbor.setSortOrder(tempOrder);

        topicRepository.saveAll(List.of(current, neighbor));
        return true;
    }

    /**
     * Kết quả chuyển nội dung (từ vựng và Kanji) từ chủ đề nguồn sang chủ đề đích.
     */
    public record TopicMoveContentResult(int movedVocabCount, int mergedVocabCount, int movedKanjiCount) {
    }

    /**
     * Chuyển toàn bộ từ vựng và Kanji của chủ đề nguồn sang chủ đề đích trong một transaction.
     * Đích phải tồn tại, khác nguồn, và cùng loại kanjiSection với nguồn.
     * Từ chuyển sang bị trùng (topic, japaneseText) với từ đã có ở đích thì xóa bản ghi nguồn trùng
     * và tăng bộ đếm mergedVocabCount.
     */
    public TopicMoveContentResult moveContent(Long sourceTopicId, Long targetTopicId) {
        if (sourceTopicId == null || sourceTopicId <= 0) {
            throw new IllegalArgumentException("ID chủ đề nguồn không hợp lệ.");
        }
        if (targetTopicId == null || targetTopicId <= 0) {
            throw new IllegalArgumentException("ID chủ đề đích không hợp lệ.");
        }
        if (sourceTopicId.equals(targetTopicId)) {
            throw new IllegalArgumentException("Chủ đề đích phải khác chủ đề nguồn.");
        }

        Topic source = topicRepository.findById(sourceTopicId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chủ đề nguồn với ID: " + sourceTopicId));
        Topic target = topicRepository.findById(targetTopicId)
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề đích không tồn tại."));

        if (source.isKanjiSection() != target.isKanjiSection()) {
            throw new IllegalArgumentException("Chủ đề đích phải cùng loại phân loại với chủ đề nguồn.");
        }

        List<Vocabulary> sourceVocabs = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(sourceTopicId);
        int movedVocabCount = 0;
        int mergedVocabCount = 0;

        for (Vocabulary v : sourceVocabs) {
            if (vocabularyRepository.existsByTopicIdAndJapaneseText(targetTopicId, v.getJapaneseText())) {
                vocabularyRepository.delete(v);
                mergedVocabCount++;
            } else {
                v.setTopic(target);
                vocabularyRepository.save(v);
                movedVocabCount++;
            }
        }
        vocabularyRepository.flush();

        List<KanjiEntry> sourceKanjis = kanjiEntryRepository.findByTopicIdOrderBySourceLineAsc(sourceTopicId);
        for (KanjiEntry k : sourceKanjis) {
            k.setTopic(target);
        }
        if (!sourceKanjis.isEmpty()) {
            kanjiEntryRepository.saveAll(sourceKanjis);
            kanjiEntryRepository.flush();
        }

        return new TopicMoveContentResult(movedVocabCount, mergedVocabCount, sourceKanjis.size());
    }

    /**
     * Lấy danh sách các chủ đề đích hợp lệ (khác nguồn và cùng loại kanjiSection) để chuyển nội dung.
     */
    @Transactional(readOnly = true)
    public List<Topic> findMoveContentTargets(Long sourceTopicId) {
        if (sourceTopicId == null || sourceTopicId <= 0) {
            return Collections.emptyList();
        }
        Optional<Topic> sourceOpt = topicRepository.findById(sourceTopicId);
        if (sourceOpt.isEmpty()) {
            return Collections.emptyList();
        }
        boolean isKanji = sourceOpt.get().isKanjiSection();
        return topicRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> !Objects.equals(t.getId(), sourceTopicId) && t.isKanjiSection() == isKanji)
                .toList();
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
