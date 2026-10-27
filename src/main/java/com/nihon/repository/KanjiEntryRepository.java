package com.nihon.repository;

import com.nihon.entity.KanjiEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KanjiEntryRepository extends JpaRepository<KanjiEntry, Long> {

    List<KanjiEntry> findByTopicIdOrderBySourceLineAsc(Long topicId);

    Optional<KanjiEntry> findByCharacter(String character);

    Optional<KanjiEntry> findBySourceLine(Integer sourceLine);

    boolean existsByTopicId(Long topicId);

    long countByTopicId(Long topicId);

    @Query(
        "SELECT k FROM KanjiEntry k " +
        "JOIN k.topic t " +
        "LEFT JOIN t.parent p " +
        "WHERE (:topicId IS NULL OR t.id = :topicId OR p.id = :topicId) " +
        "  AND (:keyword IS NULL OR :keyword = '' OR " +
        "       k.character LIKE CONCAT('%', :keyword, '%') OR " +
        "       LOWER(k.hanViet) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
        "       LOWER(k.meaningVi) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
        "       k.onyomi LIKE CONCAT('%', :keyword, '%') OR " +
        "       k.kunyomi LIKE CONCAT('%', :keyword, '%'))"
    )
    Page<KanjiEntry> search(
        @Param("topicId") Long topicId,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    @Query("SELECT COALESCE(MAX(k.sourceLine), 0) FROM KanjiEntry k")
    Integer findMaxSourceLine();
}
