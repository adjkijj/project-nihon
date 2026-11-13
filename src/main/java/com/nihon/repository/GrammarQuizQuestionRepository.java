package com.nihon.repository;

import com.nihon.entity.GrammarQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrammarQuizQuestionRepository extends JpaRepository<GrammarQuizQuestion, Long> {

    List<GrammarQuizQuestion> findByGrammarTopicIdOrderBySortOrderAsc(Long grammarId);

    @Query("SELECT MAX(q.sortOrder) FROM GrammarQuizQuestion q WHERE q.grammarTopic.id = :grammarId")
    Integer findMaxSortOrderByGrammarTopicId(@Param("grammarId") Long grammarId);

    long countByGrammarTopicId(Long grammarId);

    @Query("SELECT DISTINCT q FROM GrammarQuizQuestion q LEFT JOIN FETCH q.choices WHERE q.id = :id")
    Optional<GrammarQuizQuestion> findByIdWithChoices(@Param("id") Long id);
}

