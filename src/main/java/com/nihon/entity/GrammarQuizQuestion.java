package com.nihon.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể lưu trữ câu hỏi luyện tập trắc nghiệm của chủ đề ngữ pháp (F30).
 */
@Entity
@Table(name = "grammar_quiz_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarQuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grammar_id", nullable = false)
    private GrammarTopic grammarTopic;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "prompt", nullable = false, length = 500)
    private String prompt;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<GrammarQuizChoice> choices = new ArrayList<>();

    public void addChoice(GrammarQuizChoice choice) {
        if (choices == null) {
            choices = new ArrayList<>();
        }
        choices.add(choice);
        choice.setQuestion(this);
    }

    public void removeChoice(GrammarQuizChoice choice) {
        if (choices != null) {
            choices.remove(choice);
            choice.setQuestion(null);
        }
    }

    /**
     * Quy tắc F30: Mỗi câu hỏi trắc nghiệm phải có từ 2 đến 6 lựa chọn và đúng 1 lựa chọn đúng.
     */
    public boolean isValid() {
        if (choices == null || choices.size() < 2 || choices.size() > 6) {
            return false;
        }
        long correctCount = choices.stream().filter(GrammarQuizChoice::isCorrect).count();
        return correctCount == 1;
    }
}

