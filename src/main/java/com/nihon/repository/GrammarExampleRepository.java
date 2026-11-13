package com.nihon.repository;

import com.nihon.entity.GrammarExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrammarExampleRepository extends JpaRepository<GrammarExample, Long> {

    List<GrammarExample> findByGrammarTopicIdOrderBySortOrderAsc(Long grammarId);

    @Query("SELECT MAX(e.sortOrder) FROM GrammarExample e WHERE e.grammarTopic.id = :grammarId")
    Integer findMaxSortOrderByGrammarTopicId(@Param("grammarId") Long grammarId);

    long countByGrammarTopicId(Long grammarId);
}

