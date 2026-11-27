package com.nihon.repository;

import com.nihon.entity.ExamChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamChoiceRepository extends JpaRepository<ExamChoice, Long> {

    List<ExamChoice> findByQuestionIdOrderBySortOrderAsc(Long questionId);

    void deleteByQuestionId(Long questionId);
}

