package com.nihon.dto;

import java.util.List;

/**
 * Kết quả phân tích file từ vựng (chưa ghi database).
 */
public record ParsedVocabularyFile(
        List<ParsedTopic> topics,
        List<ParsedVocabulary> vocabularies,
        List<ParsedKanji> kanji,
        int rowsRead,
        int skippedDuplicateInFile,
        int readingNotProvided,
        int readingAmbiguous,
        List<ImportIssue> errors) {

    /** Tiêu đề "##" (parentLine = null) hoặc "###". */
    public record ParsedTopic(int sourceLine, String name, Integer parentLine, boolean kanjiSection) {
    }

    /** reading = null khi nguồn không cung cấp hoặc không xác định được duy nhất. */
    public record ParsedVocabulary(int sourceLine, int topicLine, String japaneseText, String reading,
                                   String romaji, String meaningVi, boolean hasKanji) {
    }

    public record ParsedKanji(int sourceLine, int topicLine, String character, String hanViet,
                              String meaningVi, String onyomi, String kunyomi) {
    }
}
