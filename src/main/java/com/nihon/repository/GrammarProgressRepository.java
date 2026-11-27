package com.nihon.repository;

import com.nihon.entity.GrammarProgress;
import com.nihon.entity.GrammarProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository truy xuất tiến độ học ngữ pháp (F30).
 */
@Repository
public interface GrammarProgressRepository extends JpaRepository<GrammarProgress, GrammarProgressId> {

    boolean existsByIdLearnerIdAndIdGrammarId(String learnerId, Long grammarId);

    Optional<GrammarProgress> findByIdLearnerIdAndIdGrammarId(String learnerId, Long grammarId);

    long countByIdLearnerId(String learnerId);

    List<GrammarProgress> findByIdLearnerId(String learnerId);

    @Query("SELECT gp.id.grammarId FROM GrammarProgress gp WHERE gp.id.learnerId = :learnerId")
    Set<Long> findLearnedGrammarIdsByLearnerId(@Param("learnerId") String learnerId);

    @Modifying
    @Query("DELETE FROM GrammarProgress gp WHERE gp.id.learnerId = :learnerId AND gp.id.grammarId = :grammarId")
    void deleteByLearnerIdAndGrammarId(@Param("learnerId") String learnerId, @Param("grammarId") Long grammarId);

    @Modifying
    @Query("DELETE FROM GrammarProgress gp WHERE gp.learnedAt < :cutoff")
    int deleteByLearnedAtBefore(@Param("cutoff") Instant cutoff);
}

