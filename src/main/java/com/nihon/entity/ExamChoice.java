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

/**
 * Thực thể Lựa chọn trong Câu hỏi thi (F41).
 * Dùng cho các phương án trắc nghiệm hoặc các mảnh từ trong bài ghép câu (ORDERING).
 */
@Entity
@Table(name = "exam_choice",
        indexes = @Index(name = "idx_exam_choice_question_sort", columnList = "question_id, sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ExamQuestion question;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Builder.Default
    @Column(name = "correct", nullable = false)
    private boolean correct = false;
}

