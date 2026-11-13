package com.nihon.dto.grammar;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * Form khởi tạo đấu trường phản xạ ngữ pháp (F33).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarArenaStartForm implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final Set<Integer> ALLOWED_DURATIONS = Set.of(2, 5, 10);
    public static final Set<Integer> ALLOWED_QUESTION_COUNTS = Set.of(10, 20, 30);

    @Pattern(regexp = "^(ALL|GRAMMAR|SUPPLEMENT)$", message = "Phân loại phải là ALL, GRAMMAR hoặc SUPPLEMENT.")
    @Builder.Default
    private String category = "ALL";

    private Long topicId;

    @NotNull(message = "Vui lòng chọn thời lượng đấu trường.")
    @Builder.Default
    private Integer durationMinutes = 5;

    @NotNull(message = "Vui lòng chọn số lượng câu hỏi.")
    @Builder.Default
    private Integer questionCount = 10;
}

