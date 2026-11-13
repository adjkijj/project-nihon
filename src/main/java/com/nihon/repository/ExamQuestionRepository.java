package com.nihon.repository;

import com.nihon.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findBySectionIdOrderBySortOrderAsc(Long sectionId);

    long countBySectionPaperId(Long paperId);

    @Query("SELECT DISTINCT q FROM ExamQuestion q " +
            "LEFT JOIN FETCH q.choices " +
            "WHERE q.id = :id")
    Optional<ExamQuestion> findByIdWithChoices(@Param("id") Long id);
}

