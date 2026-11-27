package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một phiên học đã hoàn tất của một người học ẩn danh (chỉ lưu số liệu tổng hợp). */
@Entity
@Table(name = "study_session",
        indexes = @Index(name = "idx_study_session_learner_completed", columnList = "learner_id, completed_at"))
@Getter
@Setter
@NoArgsConstructor
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "learner_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String learnerId;

    @Column(name = "mode", nullable = false, length = 40)
    private String mode;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}