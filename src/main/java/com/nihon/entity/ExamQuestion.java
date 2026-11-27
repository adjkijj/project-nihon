package com.nihon.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Thực thể Câu hỏi trong Đề thi (F41).
 */
@Entity
@Table(name = "exam_question",
        indexes = @Index(name = "idx_exam_question_section_sort", columnList = "section_id, sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private ExamSection section;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ExamQuestionType type;

    @Column(name = "prompt_ja", length = 1000)
    private String promptJa;

    @Column(name = "prompt_vi", length = 1000)
    private String promptVi;

    @Builder.Default
    @Column(name = "points", nullable = false, precision = 5, scale = 2)
    private BigDecimal points = BigDecimal.valueOf(1.00);

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ExamChoice> choices = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamAnswerKey> answerKeys = new ArrayList<>();

    public void addChoice(ExamChoice choice) {
        if (choice != null) {
            choices.add(choice);
            choice.setQuestion(this);
        }
    }

    public void removeChoice(ExamChoice choice) {
        if (choice != null) {
            choices.remove(choice);
            choice.setQuestion(null);
        }
    }

    public void addAnswerKey(ExamAnswerKey answerKey) {
        if (answerKey != null) {
            answerKeys.add(answerKey);
            answerKey.setQuestion(this);
        }
    }

    public void removeAnswerKey(ExamAnswerKey answerKey) {
        if (answerKey != null) {
            answerKeys.remove(answerKey);
            answerKey.setQuestion(null);
        }
    }

    public String getPromptDisplay() {
        if (promptJa != null && !promptJa.isBlank()) {
            if (promptVi != null && !promptVi.isBlank()) {
                return promptJa + " (" + promptVi + ")";
            }
            return promptJa;
        }
        return promptVi != null ? promptVi : "";
    }
}

