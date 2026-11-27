package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thực thể quản lý Động từ tiếng Nhật và nhóm động từ (F13, F16).
 * Lưu thể từ điển (dictionary_form), thể ます (masu_form), chữ Hán (kanji_text),
 * nhóm động từ (1, 2, 3), nghĩa tiếng Việt và chủ đề liên kết.
 */
@Entity
@Table(name = "verb",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_verb_dict_kanji", columnNames = {"dictionary_form", "kanji_text"})
        },
        indexes = {
                @Index(name = "idx_verb_dictionary_form", columnList = "dictionary_form"),
                @Index(name = "idx_verb_topic_id", columnList = "topic_id"),
                @Index(name = "idx_verb_verb_group", columnList = "verb_group")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Verb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", foreignKey = @ForeignKey(name = "fk_verb_topic"))
    private Topic topic;

    @Column(name = "masu_form", nullable = false, length = 100)
    private String masuForm;

    @Column(name = "dictionary_form", nullable = false, length = 100)
    private String dictionaryForm;

    @Column(name = "kanji_text", length = 100)
    private String kanjiText;

    @Column(name = "verb_group", nullable = false)
    private Integer verbGroup;

    @Column(name = "meaning_vi", length = 255)
    private String meaningVi;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        if (updatedAt != null && !now.isAfter(updatedAt)) {
            updatedAt = updatedAt.plusNanos(1000);
        } else {
            updatedAt = now;
        }
    }
}
