package com.nihon.repository;

import com.nihon.entity.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamSectionRepository extends JpaRepository<ExamSection, Long> {

    List<ExamSection> findByPaperIdOrderBySortOrderAsc(Long paperId);

    long countByPaperId(Long paperId);

    @Query("SELECT DISTINCT s FROM ExamSection s " +
            "LEFT JOIN FETCH s.questions q " +
            "WHERE s.id = :id")
    Optional<ExamSection> findByIdWithQuestions(@Param("id") Long id);
}

