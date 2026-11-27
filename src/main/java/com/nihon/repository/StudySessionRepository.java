package com.nihon.repository;

import com.nihon.entity.StudySession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Mọi truy vấn đều lọc theo learner_id (trừ dọn dẹp theo hạn lưu trữ). */
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    interface Totals {
        Long getSessions();

        Long getQuestions();

        Long getCorrect();

        Long getSeconds();
    }

    @Query("select s from StudySession s where s.learnerId = :learnerId "
            + "and (:mode = 'ALL' or s.mode = :mode) and s.completedAt >= :since "
            + "order by s.completedAt desc, s.id desc")
    Page<StudySession> findPage(@Param("learnerId") String learnerId, @Param("mode") String mode,
                                @Param("since") LocalDateTime since, Pageable pageable);

    @Query("select count(s) as sessions, coalesce(sum(s.totalQuestions),0) as questions, "
            + "coalesce(sum(s.correctCount),0) as correct, coalesce(sum(s.durationSeconds),0) as seconds "
            + "from StudySession s where s.learnerId = :learnerId "
            + "and (:mode = 'ALL' or s.mode = :mode) and s.completedAt >= :since")
    Totals totals(@Param("learnerId") String learnerId, @Param("mode") String mode,
                  @Param("since") LocalDateTime since);

    @Query("select s from StudySession s where s.learnerId = :learnerId "
            + "and (:mode = 'ALL' or s.mode = :mode) and s.completedAt >= :since")
    List<StudySession> findSince(@Param("learnerId") String learnerId, @Param("mode") String mode,
                                 @Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from StudySession s where s.learnerId = :learnerId")
    int deleteAllByLearner(@Param("learnerId") String learnerId);

    @Modifying
    @Query("delete from StudySession s where s.completedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}