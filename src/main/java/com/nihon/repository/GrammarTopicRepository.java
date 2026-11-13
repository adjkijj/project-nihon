package com.nihon.repository;

import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrammarTopicRepository extends JpaRepository<GrammarTopic, Long>, JpaSpecificationExecutor<GrammarTopic> {

    Optional<GrammarTopic> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT MAX(g.sortOrder) FROM GrammarTopic g")
    Integer findMaxSortOrder();

    List<GrammarTopic> findByTopicIdOrderBySortOrderAsc(Long topicId);

    List<GrammarTopic> findByCategoryOrderBySortOrderAsc(GrammarCategory category);

    @Query("SELECT DISTINCT g FROM GrammarTopic g LEFT JOIN FETCH g.topic LEFT JOIN FETCH g.examples WHERE g.id = :id")
    Optional<GrammarTopic> findByIdWithExamples(@Param("id") Long id);

    @Query("SELECT DISTINCT g FROM GrammarTopic g LEFT JOIN FETCH g.questions q LEFT JOIN FETCH q.choices WHERE g.id = :id")
    Optional<GrammarTopic> findByIdWithQuestions(@Param("id") Long id);
}

