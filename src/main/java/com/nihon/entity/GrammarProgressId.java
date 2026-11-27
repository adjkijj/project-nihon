package com.nihon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Khóa chính ghép cho tiến độ học ngữ pháp (learner_id, grammar_id).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GrammarProgressId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "learner_id", length = 36, nullable = false)
    private String learnerId;

    @Column(name = "grammar_id", nullable = false)
    private Long grammarId;
}

