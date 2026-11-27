package com.nihon.repository;

import com.nihon.entity.ExamAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {
    List<ExamAttemptAnswer> findByAttemptId(String attemptId);
}

