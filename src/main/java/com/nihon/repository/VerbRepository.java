package com.nihon.repository;

import com.nihon.entity.Verb;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VerbRepository extends JpaRepository<Verb, Long> {

    @Query(value = "SELECT v FROM Verb v LEFT JOIN FETCH v.topic t WHERE " +
           "(:verbGroup IS NULL OR v.verbGroup = :verbGroup) AND " +
           "(:topicId IS NULL OR v.topic.id = :topicId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(v.dictionaryForm) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.masuForm) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(v.kanjiText IS NOT NULL AND LOWER(v.kanjiText) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(v.meaningVi IS NOT NULL AND LOWER(v.meaningVi) LIKE LOWER(CONCAT('%', :keyword, '%'))))",
           countQuery = "SELECT COUNT(v) FROM Verb v WHERE " +
           "(:verbGroup IS NULL OR v.verbGroup = :verbGroup) AND " +
           "(:topicId IS NULL OR v.topic.id = :topicId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(v.dictionaryForm) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(v.masuForm) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(v.kanjiText IS NOT NULL AND LOWER(v.kanjiText) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(v.meaningVi IS NOT NULL AND LOWER(v.meaningVi) LIKE LOWER(CONCAT('%', :keyword, '%'))))")
    Page<Verb> search(@Param("verbGroup") Integer verbGroup,
                      @Param("topicId") Long topicId,
                      @Param("keyword") String keyword,
                      Pageable pageable);

    @Query("SELECT v FROM Verb v LEFT JOIN FETCH v.topic WHERE v.id = :id")
    Optional<Verb> findByIdWithTopic(@Param("id") Long id);

    @Query("SELECT COUNT(v) > 0 FROM Verb v WHERE v.dictionaryForm = :dict AND " +
           "((:kanji IS NULL AND v.kanjiText IS NULL) OR v.kanjiText = :kanji)")
    boolean existsByDictionaryFormAndKanjiText(@Param("dict") String dict, @Param("kanji") String kanji);

    @Query("SELECT COUNT(v) > 0 FROM Verb v WHERE v.dictionaryForm = :dict AND " +
           "((:kanji IS NULL AND v.kanjiText IS NULL) OR v.kanjiText = :kanji) AND v.id <> :id")
    boolean existsByDictionaryFormAndKanjiTextAndIdNot(@Param("dict") String dict, @Param("kanji") String kanji, @Param("id") Long id);

    List<Verb> findByTopicId(Long topicId);

    long countByTopicId(Long topicId);
}
