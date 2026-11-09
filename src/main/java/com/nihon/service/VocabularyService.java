package com.nihon.service;

import com.nihon.dto.VocabularyDeduplicationKey;
import com.nihon.dto.VocabularyDetailDto;
import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.validation.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class VocabularyService {

    private static final Logger log = LoggerFactory.getLogger(VocabularyService.class);
    public static final int MAX_KEYWORD_LENGTH = 50;
    public static final int MAX_BULK_IDS = 200;
    private static final int LARGE_DATASET_WARN_THRESHOLD = 20000;

    public record BulkDeleteResult(int deletedCount, int skippedCount) {}
    public record BulkMoveResult(int movedCount, int mergedCount, int skippedCount) {}

    private final VocabularyRepository vocabularyRepository;
    private final TopicRepository topicRepository;
    private final KanjiEntryRepository kanjiEntryRepository;

    public VocabularyService(VocabularyRepository vocabularyRepository, TopicRepository topicRepository) {
        this(vocabularyRepository, topicRepository, null);
    }

    @Autowired
    public VocabularyService(VocabularyRepository vocabularyRepository,
                             TopicRepository topicRepository,
                             KanjiEntryRepository kanjiEntryRepository) {
        this.vocabularyRepository = vocabularyRepository;
        this.topicRepository = topicRepository;
        this.kanjiEntryRepository = kanjiEntryRepository;
    }

    @Transactional(readOnly = true)
    public Page<VocabularyRowDto> search(Long topicId, String keyword, Pageable pageable) {
        // Tải toàn bộ từ vựng kèm Topic trong 1 câu SQL duy nhất (JOIN FETCH) để chống N+1
        List<Vocabulary> allVocabs = vocabularyRepository.findAllWithTopic();
        if (allVocabs.size() > LARGE_DATASET_WARN_THRESHOLD) {
            log.warn("Số lượng bản ghi từ vựng nạp vào bộ nhớ vượt ngưỡng cảnh báo ({} > {}). Cần chuyển sang truy vấn phân trang ở DB khi dữ liệu lớn.",
                    allVocabs.size(), LARGE_DATASET_WARN_THRESHOLD);
        }

        // Nhóm các từ vựng theo khóa định danh chuẩn hóa (Japanese + Reading + MeaningVi)
        Map<VocabularyDeduplicationKey, List<Vocabulary>> grouped = new LinkedHashMap<>();
        for (Vocabulary v : allVocabs) {
            VocabularyDeduplicationKey key = VocabularyDeduplicationKey.of(v);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        String sanitized = sanitizeKeyword(keyword);
        String cleanKeyword = (sanitized != null) ? sanitized.toLowerCase(Locale.ROOT) : null;
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
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, pageable.getPageSize());
        long fromIndexLong = (long) page * (long) size;
        List<VocabularyRowDto> pageContent;
        if (fromIndexLong >= total || fromIndexLong < 0) {
            pageContent = Collections.emptyList();
        } else {
            int fromIndex = (int) fromIndexLong;
            int toIndex = (int) Math.min(fromIndexLong + size, total);
            pageContent = rows.subList(fromIndex, toIndex);
        }

        return new PageImpl<>(pageContent, pageable, total);
    }

    public String sanitizeKeyword(String keyword) {
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
        if (id == null || id <= 0) return Optional.empty();
        return vocabularyRepository.findById(id);
    }

    public Vocabulary create(VocabularyForm form) {
        Topic topic = topicRepository.findById(form.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + form.getTopicId()));

        String normalizedJapanese = TextNormalizer.normalize(form.getJapaneseText());
        if (normalizedJapanese == null) {
            throw new IllegalArgumentException("Tiếng Nhật không được để trống.");
        }

        if (vocabularyRepository.existsByTopicIdAndJapaneseText(topic.getId(), normalizedJapanese)) {
            throw new IllegalArgumentException("Từ này đã tồn tại trong chủ đề đã chọn.");
        }

        Vocabulary v = new Vocabulary();
        v.setTopic(topic);
        v.setJapaneseText(normalizedJapanese);
        v.setReading(TextNormalizer.normalize(form.getReading()));
        v.setRomaji(TextNormalizer.normalize(form.getRomaji()));
        v.setMeaningVi(TextNormalizer.normalize(form.getMeaningVi()));
        v.setHasKanji(VocabularyFileParser.containsKanji(normalizedJapanese));
        v.setSourceLine(null);

        return vocabularyRepository.save(v);
    }

    public Vocabulary update(Long id, VocabularyForm form) {
        Vocabulary v = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));

        Topic topic = topicRepository.findById(form.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + form.getTopicId()));

        String normalizedJapanese = TextNormalizer.normalize(form.getJapaneseText());
        if (normalizedJapanese == null) {
            throw new IllegalArgumentException("Tiếng Nhật không được để trống.");
        }

        if (vocabularyRepository.existsByTopicIdAndJapaneseTextAndIdNot(topic.getId(), normalizedJapanese, id)) {
            throw new IllegalArgumentException("Từ này đã tồn tại trong chủ đề đã chọn.");
        }

        v.setTopic(topic);
        v.setJapaneseText(normalizedJapanese);
        v.setReading(TextNormalizer.normalize(form.getReading()));
        v.setRomaji(TextNormalizer.normalize(form.getRomaji()));
        v.setMeaningVi(TextNormalizer.normalize(form.getMeaningVi()));
        v.setHasKanji(VocabularyFileParser.containsKanji(normalizedJapanese));

        return vocabularyRepository.save(v);
    }

    public void delete(Long id) {
        Vocabulary v = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));
        vocabularyRepository.delete(v);
    }

    /**
     * Xóa duy nhất bản ghi của chủ đề đang lọc (theo ID đại diện).
     */
    public int deleteInTopic(Long id) {
        Vocabulary v = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));
        vocabularyRepository.delete(v);
        return 1;
    }

    /**
     * Xóa toàn bộ các bản ghi trong nhóm có cùng VocabularyDeduplicationKey với bản ghi đại diện.
     */
    public int deleteGroup(Long id) {
        return deleteGroup(id, null);
    }

    /**
     * Xóa các bản ghi trong nhóm có cùng VocabularyDeduplicationKey với bản ghi đại diện,
     * đối chiếu với danh sách ID client gửi lên và bỏ qua mọi ID không thuộc nhóm.
     */
    public int deleteGroup(Long id, List<Long> clientIds) {
        Vocabulary rep = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));
        List<Vocabulary> targetGroup = resolveTargetGroup(rep, clientIds);
        vocabularyRepository.deleteAll(targetGroup);
        return targetGroup.size();
    }

    /**
     * Cập nhật các trường văn bản cho toàn bộ bản ghi trong nhóm của bản ghi đại diện
     * để chúng tiếp tục được gom nhóm; đổi chủ đề (topicId) chỉ áp dụng cho bản ghi đại diện.
     */
    public List<Vocabulary> updateGroup(Long id, VocabularyForm form) {
        List<Long> clientIds = (form != null) ? form.getAllIds() : null;
        return updateGroup(id, form, clientIds);
    }

    /**
     * Cập nhật nhóm từ vựng trong một transaction, kiểm tra ràng buộc trùng (topic, japaneseText)
     * cho từng bản ghi bị ảnh hưởng trước khi ghi. Nếu bất kỳ bản ghi nào vi phạm thì dừng toàn bộ và rollback.
     */
    public List<Vocabulary> updateGroup(Long id, VocabularyForm form, List<Long> clientIds) {
        Vocabulary rep = vocabularyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy từ vựng với ID: " + id));

        Topic newRepTopic = topicRepository.findById(form.getTopicId())
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề không tồn tại với ID: " + form.getTopicId()));

        String normalizedJapanese = TextNormalizer.normalize(form.getJapaneseText());
        if (normalizedJapanese == null) {
            throw new IllegalArgumentException("Tiếng Nhật không được để trống.");
        }
        String normalizedReading = TextNormalizer.normalize(form.getReading());
        String normalizedRomaji = TextNormalizer.normalize(form.getRomaji());
        String normalizedMeaningVi = TextNormalizer.normalize(form.getMeaningVi());
        boolean hasKanji = VocabularyFileParser.containsKanji(normalizedJapanese);

        List<Vocabulary> targetGroup = resolveTargetGroup(rep, clientIds);
        if (targetGroup.stream().noneMatch(v -> Objects.equals(v.getId(), rep.getId()))) {
            targetGroup.add(0, rep);
        }

        // Kiểm tra ràng buộc trùng (topic, japaneseText) cho từng bản ghi trong nhóm trước khi sửa đổi
        Set<Long> seenTargetTopics = new HashSet<>();
        for (Vocabulary member : targetGroup) {
            Topic targetTopic = Objects.equals(member.getId(), rep.getId()) ? newRepTopic : member.getTopic();
            if (targetTopic != null) {
                if (!seenTargetTopics.add(targetTopic.getId())) {
                    throw new IllegalArgumentException("Từ này đã tồn tại trong chủ đề đã chọn.");
                }
                if (vocabularyRepository.existsByTopicIdAndJapaneseTextAndIdNot(
                        targetTopic.getId(), normalizedJapanese, member.getId())) {
                    throw new IllegalArgumentException("Từ này đã tồn tại trong chủ đề đã chọn.");
                }
            }
        }

        for (Vocabulary member : targetGroup) {
            if (Objects.equals(member.getId(), rep.getId())) {
                member.setTopic(newRepTopic);
            }
            member.setJapaneseText(normalizedJapanese);
            member.setReading(normalizedReading);
            member.setRomaji(normalizedRomaji);
            member.setMeaningVi(normalizedMeaningVi);
            member.setHasKanji(hasKanji);
        }

        return vocabularyRepository.saveAll(targetGroup);
    }

    /**
     * Tính danh sách ID của nhóm từ vựng từ ID đại diện bằng VocabularyDeduplicationKey.
     */
    @Transactional(readOnly = true)
    public List<Long> findGroupIds(Long id) {
        if (id == null || id <= 0) {
            return Collections.emptyList();
        }
        Optional<Vocabulary> repOpt = vocabularyRepository.findById(id);
        if (repOpt.isEmpty()) {
            return Collections.emptyList();
        }
        return computeServerGroup(repOpt.get()).stream()
                .map(Vocabulary::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Tự tính lại nhóm từ vựng trên server bằng VocabularyDeduplicationKey rồi đối chiếu
     * với danh sách ID client gửi lên. Mọi ID không thuộc nhóm tính trên server đều bị bỏ qua.
     */
    private List<Vocabulary> resolveTargetGroup(Vocabulary rep, List<Long> clientIds) {
        List<Vocabulary> serverGroup = computeServerGroup(rep);
        if (clientIds == null || clientIds.isEmpty()) {
            return serverGroup;
        }
        Set<Long> validClientIds = clientIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        List<Vocabulary> matched = serverGroup.stream()
                .filter(v -> validClientIds.contains(v.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (matched.isEmpty()) {
            return serverGroup;
        }
        return matched;
    }

    private List<Vocabulary> computeServerGroup(Vocabulary rep) {
        VocabularyDeduplicationKey repKey = VocabularyDeduplicationKey.of(rep);
        List<Vocabulary> serverGroup = vocabularyRepository.findAllWithTopic().stream()
                .filter(v -> repKey.equals(VocabularyDeduplicationKey.of(v)))
                .collect(Collectors.toCollection(ArrayList::new));
        if (serverGroup.stream().noneMatch(v -> Objects.equals(v.getId(), rep.getId()))) {
            serverGroup.add(0, rep);
        }
        return serverGroup;
    }

    public VocabularyForm toForm(Vocabulary v) {
        VocabularyForm form = new VocabularyForm();
        form.setId(v.getId());
        form.setTopicId(v.getTopic().getId());
        form.setJapaneseText(v.getJapaneseText());
        form.setReading(v.getReading());
        form.setRomaji(v.getRomaji());
        form.setMeaningVi(v.getMeaningVi());
        if (v.getId() != null) {
            form.setAllIds(findGroupIds(v.getId()));
        }
        return form;
    }

    /**
     * Lấy thông tin chi tiết một từ vựng kèm danh sách mọi chủ đề chứa từ (theo nhóm)
     * và danh sách chữ Kanji xuất hiện trong từ.
     */
    @Transactional(readOnly = true)
    public Optional<VocabularyDetailDto> findDetailById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        Optional<Vocabulary> repOpt = vocabularyRepository.findById(id);
        if (repOpt.isEmpty()) {
            return Optional.empty();
        }
        Vocabulary rep = repOpt.get();
        List<Vocabulary> group = computeServerGroup(rep);

        List<Topic> topics = group.stream()
                .map(Vocabulary::getTopic)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Topic::getId, t -> t, (existing, replacement) -> existing, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(Topic::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Long> allIds = group.stream()
                .map(Vocabulary::getId)
                .filter(Objects::nonNull)
                .toList();

        List<VocabularyDetailDto.KanjiInWordDto> kanjis = extractKanjisInWord(rep.getJapaneseText());

        return Optional.of(VocabularyDetailDto.builder()
                .id(rep.getId())
                .japaneseText(rep.getJapaneseText())
                .reading(rep.getReading())
                .romaji(rep.getRomaji())
                .meaningVi(rep.getMeaningVi())
                .hasKanji(rep.isHasKanji())
                .createdAt(rep.getCreatedAt())
                .updatedAt(rep.getUpdatedAt())
                .topics(topics)
                .allIds(allIds)
                .kanjis(kanjis)
                .build());
    }

    private List<VocabularyDetailDto.KanjiInWordDto> extractKanjisInWord(String japaneseText) {
        if (japaneseText == null || japaneseText.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> kanjiChars = new LinkedHashSet<>();
        for (int cp : japaneseText.codePoints().toArray()) {
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                kanjiChars.add(new String(Character.toChars(cp)));
            }
        }
        if (kanjiChars.isEmpty()) {
            return Collections.emptyList();
        }
        List<VocabularyDetailDto.KanjiInWordDto> result = new ArrayList<>();
        for (String ch : kanjiChars) {
            Optional<KanjiEntry> entryOpt = (kanjiEntryRepository != null)
                    ? kanjiEntryRepository.findByCharacter(ch)
                    : Optional.empty();
            if (entryOpt.isPresent()) {
                KanjiEntry entry = entryOpt.get();
                result.add(VocabularyDetailDto.KanjiInWordDto.builder()
                        .character(ch)
                        .kanjiId(entry.getId())
                        .hanViet(entry.getHanViet())
                        .meaningVi(entry.getMeaningVi())
                        .build());
            } else {
                result.add(VocabularyDetailDto.KanjiInWordDto.builder()
                        .character(ch)
                        .kanjiId(null)
                        .hanViet(null)
                        .meaningVi(null)
                        .build());
            }
        }
        return result;
    }

    /**
     * Xóa hàng loạt từ vựng (mặc định theo đúng các bản ghi được truyền vào).
     */
    public BulkDeleteResult bulkDelete(List<Long> ids) {
        return bulkDelete(ids, "TOPIC");
    }

    /**
     * Xóa hàng loạt từ vựng trong một transaction.
     * Nếu scope="ALL" (khi không lọc chủ đề), xóa toàn bộ các bản ghi cùng nhóm với từng ID đại diện.
     * Nếu scope="TOPIC" (khi đang lọc theo một chủ đề), chỉ xóa bản ghi tương ứng với từng ID.
     */
    public BulkDeleteResult bulkDelete(List<Long> ids, String scope) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một từ vựng.");
        }
        if (ids.size() > MAX_BULK_IDS) {
            throw new IllegalArgumentException("Chỉ được phép thao tác tối đa " + MAX_BULK_IDS + " từ vựng mỗi lần.");
        }

        Map<VocabularyDeduplicationKey, List<Vocabulary>> serverGroups = null;
        if ("ALL".equalsIgnoreCase(scope)) {
            serverGroups = new LinkedHashMap<>();
            for (Vocabulary item : vocabularyRepository.findAllWithTopic()) {
                serverGroups.computeIfAbsent(VocabularyDeduplicationKey.of(item), k -> new ArrayList<>()).add(item);
            }
        }

        Map<Long, Vocabulary> toDelete = new LinkedHashMap<>();
        int skippedCount = 0;

        for (Long rawId : ids) {
            if (rawId == null || rawId <= 0) {
                skippedCount++;
                continue;
            }
            Optional<Vocabulary> opt = vocabularyRepository.findById(rawId);
            if (opt.isEmpty()) {
                skippedCount++;
                continue;
            }
            Vocabulary v = opt.get();
            if (serverGroups != null) {
                List<Vocabulary> group = serverGroups.getOrDefault(VocabularyDeduplicationKey.of(v), List.of(v));
                for (Vocabulary member : group) {
                    toDelete.put(member.getId(), member);
                }
            }
            toDelete.put(v.getId(), v);
        }

        if (!toDelete.isEmpty()) {
            vocabularyRepository.deleteAll(toDelete.values());
        }

        return new BulkDeleteResult(toDelete.size(), skippedCount);
    }

    /**
     * Chuyển hàng loạt từ vựng sang chủ đề đích (mặc định theo đúng các bản ghi được truyền vào).
     */
    public BulkMoveResult bulkMove(List<Long> ids, Long targetTopicId) {
        return bulkMove(ids, targetTopicId, "TOPIC");
    }

    /**
     * Chuyển hàng loạt từ vựng sang chủ đề đích trong một transaction.
     * Từ bị trùng (topicId, japaneseText) với từ đã có ở chủ đề đích sẽ được gộp (xóa bản ghi nguồn).
     */
    public BulkMoveResult bulkMove(List<Long> ids, Long targetTopicId, String scope) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một từ vựng.");
        }
        if (ids.size() > MAX_BULK_IDS) {
            throw new IllegalArgumentException("Chỉ được phép thao tác tối đa " + MAX_BULK_IDS + " từ vựng mỗi lần.");
        }
        if (targetTopicId == null || targetTopicId <= 0) {
            throw new IllegalArgumentException("Chủ đề đích không hợp lệ.");
        }
        Topic targetTopic = topicRepository.findById(targetTopicId)
                .orElseThrow(() -> new IllegalArgumentException("Chủ đề đích không tồn tại."));

        Map<VocabularyDeduplicationKey, List<Vocabulary>> serverGroups = null;
        if ("ALL".equalsIgnoreCase(scope)) {
            serverGroups = new LinkedHashMap<>();
            for (Vocabulary item : vocabularyRepository.findAllWithTopic()) {
                serverGroups.computeIfAbsent(VocabularyDeduplicationKey.of(item), k -> new ArrayList<>()).add(item);
            }
        }

        Map<Long, Vocabulary> toProcess = new LinkedHashMap<>();
        int skippedCount = 0;

        for (Long rawId : ids) {
            if (rawId == null || rawId <= 0) {
                skippedCount++;
                continue;
            }
            Optional<Vocabulary> opt = vocabularyRepository.findById(rawId);
            if (opt.isEmpty()) {
                skippedCount++;
                continue;
            }
            Vocabulary v = opt.get();
            if (serverGroups != null) {
                List<Vocabulary> group = serverGroups.getOrDefault(VocabularyDeduplicationKey.of(v), List.of(v));
                for (Vocabulary member : group) {
                    toProcess.put(member.getId(), member);
                }
            }
            toProcess.put(v.getId(), v);
        }

        int movedCount = 0;
        int mergedCount = 0;

        for (Vocabulary v : toProcess.values()) {
            if (v.getTopic() != null && targetTopicId.equals(v.getTopic().getId())) {
                // Đã ở sẵn trong chủ đề đích
                continue;
            }
            if (vocabularyRepository.existsByTopicIdAndJapaneseText(targetTopicId, v.getJapaneseText())) {
                vocabularyRepository.delete(v);
                vocabularyRepository.flush();
                mergedCount++;
            } else {
                v.setTopic(targetTopic);
                vocabularyRepository.saveAndFlush(v);
                movedCount++;
            }
        }

        return new BulkMoveResult(movedCount, mergedCount, skippedCount);
    }
}
