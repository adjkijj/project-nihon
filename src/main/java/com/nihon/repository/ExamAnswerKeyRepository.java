package com.nihon.repository;

import com.nihon.entity.ExamAnswerKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAnswerKeyRepository extends JpaRepository<ExamAnswerKey, Long> {

    List<ExamAnswerKey> findByQuestionId(Long questionId);

    void deleteByQuestionId(Long questionId);
}

