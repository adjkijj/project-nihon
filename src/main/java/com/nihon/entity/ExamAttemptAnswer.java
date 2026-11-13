package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Thực thể Bảng exam_attempt_answer: Chi tiết câu trả lời và điểm số từng câu hỏi (F40 - F45).
 */
@Entity
@Table(name = "exam_attempt_answer",
        indexes = {
                @Index(name = "idx_exam_attempt_answer_attempt", columnList = "attempt_id"),
                @Index(name = "idx_exam_attempt_answer_question", columnList = "question_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "answer_value", length = 1000)
    private String answerValue;

    @Builder.Default
    @Column(name = "points_awarded", nullable = false, precision = 5, scale = 2)
    private BigDecimal pointsAwarded = BigDecimal.ZERO;

    @Column(name = "correct")
    private Boolean correct; // true = đúng, false = sai, null = tự luận chưa chấm / tự chấm

    @Builder.Default
    @Column(name = "flagged", nullable = false)
    private boolean flagged = false; // Đánh dấu xem lại
}

