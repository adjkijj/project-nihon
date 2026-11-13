package com.nihon.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Báo cáo xem trước kết quả đọc tệp Excel Ngữ pháp & Trắc nghiệm (F31).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarImportPreviewReport {

    private String token;
    private String filename;
    private long fileSize;

    @Builder.Default
    private List<GrammarTopicPreviewDto> topics = new ArrayList<>();

    @Builder.Default
    private List<GrammarQuizPreviewDto> quizQuestions = new ArrayList<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private int totalTopics;
    private int totalExamples;
    private int totalQuestions;

    public boolean canCommit() {
        return errors.isEmpty() && totalTopics > 0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrammarTopicPreviewDto {
        private int rowNum;
        private String code;
        private String title;
        private String category;
        private String formula;
        private String explanation;
        private String notes;
        @Builder.Default
        private List<GrammarExamplePreviewDto> examples = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrammarExamplePreviewDto {
        private int rowNum;
        private String japanese;
        private String reading;
        private String meaningVi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrammarQuizPreviewDto {
        private int rowNum;
        private String grammarCode;
        private String prompt;
        @Builder.Default
        private List<String> choices = new ArrayList<>();
        private int correctIndex;
        private String explanation;
    }
}

