package com.nihon.entity;

import com.nihon.validation.KanjiLimits;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một hàng của bảng "| Tiếng Nhật | Romaji | Nghĩa tiếng Việt |".
 * Các hàng trùng nội dung ở chủ đề khác nhau được giữ thành bản ghi riêng
 * (mỗi hàng nguồn = một bản ghi, định danh bằng source_line), không tự gộp/xóa.
 */
@Entity
@Table(name = "vocabulary",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vocabulary_source_line", columnNames = "source_line"),
                @UniqueConstraint(name = "uk_vocabulary_topic_japanese", columnNames = {"topic_id", "japanese_text"})
        },
        indexes = {
                @Index(name = "idx_vocabulary_topic", columnList = "topic_id"),
                @Index(name = "idx_vocabulary_japanese", columnList = "japanese_text"),
                @Index(name = "idx_vocabulary_import_batch", columnList = "import_batch_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vocabulary_topic"))
    private Topic topic;

    /** Nội dung cột "Tiếng Nhật" giữ nguyên văn, ví dụ "私（わたし）". */
    @Column(name = "japanese_text", nullable = false, length = KanjiLimits.MAX_JAPANESE_LENGTH)
    private String japaneseText;

    /**
     * Cách đọc Hiragana lấy từ ngoặc trong nguồn. NULL nếu nguồn không ghi
     * (ví dụ "1歳", "1月"); không tự suy đoán.
     */
    @Column(name = "reading", length = KanjiLimits.MAX_READING_LENGTH)
    private String reading;

    @Column(name = "romaji", length = KanjiLimits.MAX_ROMAJI_LENGTH)
    private String romaji;

    @Column(name = "meaning_vi", length = KanjiLimits.MAX_VOCAB_MEANING_LENGTH)
    private String meaningVi;

    /** Từ có chứa ký tự Kanji hay không (quyết định quy tắc kiểm tra Nhật → Việt). */
    @Column(name = "has_kanji", nullable = false)
    private boolean hasKanji;

    @Column(name = "source_line")
    private Integer sourceLine;

    /** Mã lô import (tham chiếu ImportAuditLog.id) nếu từ vựng được thêm mới từ chức năng import Excel. */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = java.time.LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (updatedAt != null && !now.isAfter(updatedAt)) {
            updatedAt = updatedAt.plusNanos(1000);
        } else {
            updatedAt = now;
        }
    }

    /**
     * Chuỗi văn bản phục vụ phát âm tiếng Nhật qua Web Speech API.
     */
    public String getSpeechText() {
        return com.nihon.service.SpeechHelper.resolveSpeechText(this.japaneseText, this.reading);
    }
}
