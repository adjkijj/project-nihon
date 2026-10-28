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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thực thể lưu các từ vựng cần nhớ chứa ký tự Kanji (bảng kanji_word).
 * Liên kết N-1 với bảng kanji_entry qua kanji_id.
 * Chống trùng lặp từ theo cặp (kanji_id, japanese).
 */
@Entity
@Table(name = "kanji_word",
        uniqueConstraints = @UniqueConstraint(name = "uk_kanji_word_kanji_japanese", columnNames = {"kanji_id", "japanese"}),
        indexes = @Index(name = "idx_kanji_word_kanji", columnList = "kanji_id"))
@Getter
@Setter
@NoArgsConstructor
public class KanjiWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kanji_id", nullable = false, foreignKey = @ForeignKey(name = "fk_kanji_word_kanji"))
    private KanjiEntry kanji;

    /** Từ vựng tiếng Nhật chứa Kanji này (ví dụ: "駅前", "駅員"). */
    @Column(name = "japanese", nullable = false, length = 255)
    private String japanese;

    /** Phiên âm Romaji tương ứng. */
    @Column(name = "romaji", length = 255)
    private String romaji;

    /** Ý nghĩa tiếng Việt của từ. */
    @Column(name = "meaning_vi", length = 500)
    private String meaningVi;

    /** Thứ tự hiển thị ưu tiên của từ trong danh sách từ cần nhớ của Kanji. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
        updatedAt = LocalDateTime.now();
    }
}
