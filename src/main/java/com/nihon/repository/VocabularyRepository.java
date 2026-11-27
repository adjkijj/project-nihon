package com.nihon.repository;

import com.nihon.entity.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    List<Vocabulary> findByTopicIdOrderBySourceLineAsc(Long topicId);

    Optional<Vocabulary> findBySourceLine(Integer sourceLine);

    Optional<Vocabulary> findByTopicIdAndJapaneseText(Long topicId, String japaneseText);

    boolean existsByTopicIdAndJapaneseText(Long topicId, String japaneseText);

    boolean existsByTopicIdAndJapaneseTextAndIdNot(Long topicId, String japaneseText, Long id);

    boolean existsByTopicId(Long topicId);

    long countByTopicId(Long topicId);

    @Query("SELECT v.topic.id, COUNT(v) FROM Vocabulary v WHERE v.topic.id IN :topicIds GROUP BY v.topic.id")
    List<Object[]> countByTopicIdInGrouped(@Param("topicIds") java.util.Collection<Long> topicIds);

    List<Vocabulary> findTop10ByJapaneseTextContainingOrderBySourceLineAsc(String character);

    @Query("SELECT v FROM Vocabulary v WHERE v.topic.id = :topicId AND v.japaneseText LIKE CONCAT('%', :character, '%') ORDER BY v.id ASC")
    List<Vocabulary> findWordsByTopicIdAndCharacter(@Param("topicId") Long topicId, @Param("character") String character);

    @Query("SELECT v FROM Vocabulary v WHERE v.japaneseText LIKE CONCAT('%', :character, '%') ORDER BY v.id ASC")
    List<Vocabulary> findWordsByCharacter(@Param("character") String character);

    @Query(value = "SELECT v FROM Vocabulary v JOIN FETCH v.topic t WHERE v.japaneseText LIKE CONCAT('%', :escapedKanji, '%') ESCAPE '\\'",
           countQuery = "SELECT COUNT(v) FROM Vocabulary v WHERE v.japaneseText LIKE CONCAT('%', :escapedKanji, '%') ESCAPE '\\'")
    Page<Vocabulary> findByJapaneseTextContainingKanji(@Param("escapedKanji") String escapedKanji, Pageable pageable);

    @Query("SELECT v FROM Vocabulary v JOIN FETCH v.topic t WHERE v.japaneseText LIKE CONCAT('%', :escapedKanji, '%') ESCAPE '\\' ORDER BY v.japaneseText ASC, v.id ASC")
    List<Vocabulary> findAllByJapaneseTextContainingKanji(@Param("escapedKanji") String escapedKanji);

    @Query("SELECT v FROM Vocabulary v WHERE " +
           "(:topicId IS NULL OR v.topic.id = :topicId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(v.japaneseText) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(v.reading IS NOT NULL AND LOWER(v.reading) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "LOWER(v.meaningVi) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Vocabulary> search(@Param("topicId") Long topicId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT v.id FROM Vocabulary v WHERE (:topicId IS NULL OR v.topic.id = :topicId)")
    List<Long> findIdsByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT v FROM Vocabulary v JOIN FETCH v.topic t ORDER BY v.id DESC")
    List<Vocabulary> findAllWithTopic();

    long countByHasKanjiTrue();

    long countByHasKanjiFalse();

    List<Vocabulary> findByImportBatchId(Long importBatchId);

    long countByImportBatchId(Long importBatchId);

    @Query("SELECT COUNT(v) FROM Vocabulary v WHERE v.importBatchId = :batchId AND v.updatedAt > :importedAt")
    long countModifiedAfterImport(@Param("batchId") Long batchId, @Param("importedAt") LocalDateTime importedAt);

    @Query("SELECT COUNT(v) > 0 FROM Vocabulary v WHERE v.topic.id = :topicId AND (v.importBatchId IS NULL OR v.importBatchId <> :batchId)")
    boolean existsByTopicIdOutsideBatch(@Param("topicId") Long topicId, @Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Vocabulary v SET v.importBatchId = :batchId, v.updatedAt = v.createdAt WHERE v.id IN :ids")
    void assignImportBatchId(@Param("ids") List<Long> ids, @Param("batchId") Long batchId);

    @Query("SELECT v FROM Vocabulary v JOIN FETCH v.topic t WHERE v.topic.id = :topicId ORDER BY v.id ASC")
    List<Vocabulary> findByTopicIdWithTopic(@Param("topicId") Long topicId);
}
