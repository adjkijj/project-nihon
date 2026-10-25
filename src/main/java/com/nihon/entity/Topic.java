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
 * Chủ đề (tiêu đề "##") hoặc chủ đề con / bài học (tiêu đề "###").
 * Chủ đề con tham chiếu tới chủ đề cha qua parent_id.
 * Không khai báo cascade: xóa chủ đề KHÔNG bao giờ xóa từ vựng/Kanji.
 * Khóa ngoại mặc định RESTRICT ở DB sẽ chặn xóa chủ đề khi còn dữ liệu tham chiếu.
 */
@Entity
@Table(name = "topic",
        uniqueConstraints = @UniqueConstraint(name = "uk_topic_source_line", columnNames = "source_line"),
        indexes = {
                @Index(name = "idx_topic_parent", columnList = "parent_id"),
                @Index(name = "idx_topic_sort", columnList = "sort_order")
        })
@Getter
@Setter
@NoArgsConstructor
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên chủ đề giữ nguyên văn từ tiêu đề nguồn. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_topic_parent"))
    private Topic parent;

    /** Thứ tự xuất hiện trong file nguồn để hiển thị đúng trình tự. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** Số dòng của tiêu đề trong file nguồn; dùng để truy vết và nhập lại không trùng. */
    @Column(name = "source_line")
    private Integer sourceLine;

    /** Đánh dấu chủ đề thuộc khu vực Kanji (mục "13. Kanji" và các bài con). */
    @Column(name = "kanji_section", nullable = false)
    private boolean kanjiSection;
}
