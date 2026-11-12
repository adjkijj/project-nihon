package com.nihon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO kết quả chấm điểm câu hỏi luyện tập Kanji (F22, F23).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeKanjiResultDto {

    private Long kanjiId;
    private String character;
    private KanjiPracticeMode mode;
    private Long topicId;
    private String topicName;

    private String userAnswer;
    private String expectedAnswer;

    /** Danh sách tất cả âm đọc hợp lệ (cho dạng KANJI_TO_READING). */
    @Builder.Default
    private List<String> allValidReadings = new ArrayList<>();

    private boolean correct;
    private String feedbackMessage;

    private String hanViet;
    private String meaningVi;
    private String onyomi;
    private String kunyomi;
}

