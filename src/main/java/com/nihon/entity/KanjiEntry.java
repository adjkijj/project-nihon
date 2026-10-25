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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một hàng của bảng "| Kanji | Hán Việt | Ý nghĩa | On | Kun |" (mục 13).
 * Giá trị giữ nguyên văn nguồn; âm Kun nguồn ghi "-" được lưu nguyên ký tự "-" (= nguồn ghi không có).
 */
@Entity
@Table(name = "kanji_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kanji_character", columnNames = "kanji_character"),
                @UniqueConstraint(name = "uk_kanji_source_line", columnNames = "source_line")
        },
        indexes = @Index(name = "idx_kanji_topic", columnList = "topic_id"))
@Getter
@Setter
@NoArgsConstructor
public class KanjiEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_kanji_topic"))
    private Topic topic;

    @Column(name = "kanji_character", nullable = false, length = 8)
    private String character;

    @Column(name = "han_viet", nullable = false, length = 100)
    private String hanViet;

    @Column(name = "meaning_vi", nullable = false, length = 255)
    private String meaningVi;

    @Column(name = "onyomi", length = 100)
    private String onyomi;

    @Column(name = "kunyomi", length = 100)
    private String kunyomi;

    @Column(name = "source_line", nullable = false)
    private Integer sourceLine;
}
