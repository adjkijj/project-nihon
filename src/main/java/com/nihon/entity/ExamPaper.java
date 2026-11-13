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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể Đề thi (F41).
 */
@Entity
@Table(name = "exam_paper",
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_paper_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_exam_paper_topic_id", columnList = "topic_id"),
                @Index(name = "idx_exam_paper_category", columnList = "category"),
                @Index(name = "idx_exam_paper_published", columnList = "published")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ExamCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Builder.Default
    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(name = "shuffle_choices", nullable = false)
    private boolean shuffleChoices = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ExamSection> sections = new ArrayList<>();

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

    public void addSection(ExamSection section) {
        if (section != null) {
            sections.add(section);
            section.setPaper(this);
        }
    }

    public void removeSection(ExamSection section) {
        if (section != null) {
            sections.remove(section);
            section.setPaper(null);
        }
    }

    public int getTotalQuestions() {
        if (sections == null || sections.isEmpty()) {
            return 0;
        }
        return sections.stream()
                .mapToInt(s -> s.getQuestions() != null ? s.getQuestions().size() : 0)
                .sum();
    }

    public BigDecimal getTotalPoints() {
        if (sections == null || sections.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sections.stream()
                .flatMap(s -> s.getQuestions() != null ? s.getQuestions().stream() : java.util.stream.Stream.empty())
                .map(q -> q.getPoints() != null ? q.getPoints() : BigDecimal.ONE)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

