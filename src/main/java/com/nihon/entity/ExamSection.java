package com.nihon.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể Phần thi trong Đề thi (F41).
 */
@Entity
@Table(name = "exam_section",
        indexes = @Index(name = "idx_exam_section_paper_sort", columnList = "paper_id, sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private ExamPaper paper;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    @Column(name = "instructions", length = 1000)
    private String instructions;

    @Builder.Default
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ExamQuestion> questions = new ArrayList<>();

    public void addQuestion(ExamQuestion question) {
        if (question != null) {
            questions.add(question);
            question.setSection(this);
        }
    }

    public void removeQuestion(ExamQuestion question) {
        if (question != null) {
            questions.remove(question);
            question.setSection(null);
        }
    }

    public BigDecimal getSectionPoints() {
        if (questions == null || questions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return questions.stream()
                .map(q -> q.getPoints() != null ? q.getPoints() : BigDecimal.ONE)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

