package com.nihon.repository;

import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {

    Optional<ExamPaper> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT p FROM ExamPaper p WHERE " +
            "(:category IS NULL OR p.category = :category) AND " +
            "(:published IS NULL OR p.published = :published) AND " +
            "(:keyword IS NULL OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ExamPaper> searchAdmin(@Param("category") ExamCategory category,
                                @Param("published") Boolean published,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    @Query("SELECT DISTINCT p FROM ExamPaper p " +
            "LEFT JOIN FETCH p.sections s " +
            "WHERE p.id = :id")
    Optional<ExamPaper> findByIdWithSections(@Param("id") Long id);

    @Query("SELECT p FROM ExamPaper p WHERE p.published = true AND " +
            "(:category IS NULL OR p.category = :category) " +
            "ORDER BY p.createdAt DESC")
    java.util.List<ExamPaper> findPublishedExams(@Param("category") ExamCategory category);

    Optional<ExamPaper> findByCodeAndPublishedTrue(String code);

    @Query("SELECT p FROM ExamPaper p LEFT JOIN FETCH p.topic t WHERE p.topic.id = :topicId AND p.published = true ORDER BY p.id ASC")
    java.util.List<ExamPaper> findPublishedByTopicId(@Param("topicId") Long topicId);
}

