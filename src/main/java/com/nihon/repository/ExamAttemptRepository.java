package com.nihon.repository;

import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.entity.ExamAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, String> {

    @Query("SELECT DISTINCT a FROM ExamAttempt a " +
            "LEFT JOIN FETCH a.answers ans " +
            "LEFT JOIN FETCH a.paper p " +
            "WHERE a.id = :id")
    Optional<ExamAttempt> findByIdWithDetails(@Param("id") String id);

    boolean existsByPaperId(Long paperId);

    List<ExamAttempt> findByLearnerIdOrderBySubmittedAtDesc(String learnerId);

    Page<ExamAttempt> findByLearnerIdAndStatusOrderBySubmittedAtDesc(String learnerId, ExamAttemptStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ExamAttempt a WHERE a.learnerId = :learnerId")
    int deleteByLearnerId(@Param("learnerId") String learnerId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ExamAttempt a WHERE a.learnerId = :learnerId AND a.id = :id")
    int deleteByLearnerIdAndId(@Param("learnerId") String learnerId, @Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ExamAttempt a WHERE a.submittedAt IS NOT NULL AND a.submittedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

