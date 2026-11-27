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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.nihon.service.grammar.GrammarTextRenderer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể lưu trữ chủ đề ngữ pháp hoặc kiến thức bổ sung (F30).
 */
@Entity
@Table(name = "grammar_topic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarTopic {

    public static final int MAX_EXAMPLES_PER_TOPIC = 20;
    public static final int MAX_QUESTIONS_PER_TOPIC = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private GrammarCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "formula", length = 500)
    private String formula;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "grammarTopic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<GrammarExample> examples = new ArrayList<>();

    @OneToMany(mappedBy = "grammarTopic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<GrammarQuizQuestion> questions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (sortOrder == null) {
            sortOrder = 1;
        }
        if (category == null) {
            category = GrammarCategory.GRAMMAR;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void addExample(GrammarExample example) {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        if (examples.size() >= MAX_EXAMPLES_PER_TOPIC) {
            throw new IllegalStateException("Một chủ đề ngữ pháp không được vượt quá " + MAX_EXAMPLES_PER_TOPIC + " ví dụ.");
        }
        examples.add(example);
        example.setGrammarTopic(this);
    }

    public void removeExample(GrammarExample example) {
        if (examples != null) {
            examples.remove(example);
            example.setGrammarTopic(null);
        }
    }

    public void addQuestion(GrammarQuizQuestion question) {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        if (questions.size() >= MAX_QUESTIONS_PER_TOPIC) {
            throw new IllegalStateException("Một chủ đề ngữ pháp không được vượt quá " + MAX_QUESTIONS_PER_TOPIC + " câu hỏi trắc nghiệm.");
        }
        questions.add(question);
        question.setGrammarTopic(this);
    }

    public void removeQuestion(GrammarQuizQuestion question) {
        if (questions != null) {
            questions.remove(question);
            question.setGrammarTopic(null);
        }
    }

    @Transient
    public String getRenderedExplanation() {
        return GrammarTextRenderer.renderExplanation(this.explanation);
    }
}

