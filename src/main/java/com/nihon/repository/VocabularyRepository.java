package com.nihon.repository;

import com.nihon.entity.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    List<Vocabulary> findByTopicIdOrderBySourceLineAsc(Long topicId);

    Optional<Vocabulary> findBySourceLine(Integer sourceLine);

    boolean existsByTopicId(Long topicId);

    long countByTopicId(Long topicId);

    List<Vocabulary> findTop10ByJapaneseTextContainingOrderBySourceLineAsc(String character);

    @Query("SELECT v FROM Vocabulary v WHERE " +
           "(:topicId IS NULL OR v.topic.id = :topicId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(v.japaneseText) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(v.reading IS NOT NULL AND LOWER(v.reading) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "LOWER(v.meaningVi) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Vocabulary> search(@Param("topicId") Long topicId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT v.id FROM Vocabulary v WHERE (:topicId IS NULL OR v.topic.id = :topicId)")
    List<Long> findIdsByTopicId(@Param("topicId") Long topicId);

    long countByHasKanjiTrue();

    long countByHasKanjiFalse();
}
