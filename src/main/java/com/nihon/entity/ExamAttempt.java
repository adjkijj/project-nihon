package com.nihon.entity;

import com.nihon.dto.exam.taker.ExamAttemptStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể Bảng exam_attempt: Lượt làm bài thi của học viên lưu trong cơ sở dữ liệu (F40 - F45).
 */
@Entity
@Table(name = "exam_attempt",
        indexes = {
                @Index(name = "idx_exam_attempt_learner_status", columnList = "learner_id, status"),
                @Index(name = "idx_exam_attempt_paper_id", columnList = "paper_id"),
                @Index(name = "idx_exam_attempt_submitted_at", columnList = "submitted_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id; // UUID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private ExamPaper paper;

    @Column(name = "learner_id", length = 36, nullable = false)
    private String learnerId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Builder.Default
    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    @Builder.Default
    @Column(name = "total_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalPoints = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "max_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPoints = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ExamAttemptStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamAttemptAnswer> answers = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addAnswer(ExamAttemptAnswer answer) {
        if (answer != null) {
            answers.add(answer);
            answer.setAttempt(this);
        }
    }
}

