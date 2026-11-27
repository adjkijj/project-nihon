package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Kết quả đúng/sai theo từng mục (không lưu nội dung câu trả lời). */
@Entity
@Table(name = "study_answer")
@Getter
@Setter
@NoArgsConstructor
public class StudyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StudySession session;

    @Column(name = "vocab_id")
    private Long vocabId;

    @Column(name = "kanji_id")
    private Long kanjiId;

    @Column(name = "correct", nullable = false)
    private boolean correct;
}