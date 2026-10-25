package com.nihon.service;

import com.nihon.dto.ParsedVocabularyFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyFileParserTest {

    private final VocabularyFileParser parser = new VocabularyFileParser();

    private ParsedVocabularyFile parse(String text) throws IOException {
        return parser.parse(new StringReader(text));
    }

    @Test
    void validDataKeepsTextVerbatimAndExtractsOnlyGivenReading() throws IOException {
        String md = """
                # Tiêu đề
                
                ## 2. Bản thân
                
                | Tiếng Nhật   | Romaji  | Nghĩa tiếng Việt |
                | ------------ | ------- | ---------------- |
                | 私（わたし） | watashi | Tôi              |
                | 1歳          | issai   | Một tuổi         |
                | 初(はじ)めまして | hajimemashite | Rất hân hạnh được gặp bạn |
                | カメラ       | kamera  | Máy ảnh          |
                
                ## 13. Kanji
                
                ### Kanji Lesson 5
                
                | Kanji | Hán Việt | Ý nghĩa | On | Kun |
                | --- | --- | --- | --- | --- |
                | 駅 | DỊCH | Ga tàu | えき | - |
                """;
        ParsedVocabularyFile r = parse(md);

        assertThat(r.errors()).isEmpty();
        assertThat(r.rowsRead()).isEqualTo(5);
        assertThat(r.topics()).hasSize(3);
        assertThat(r.topics().get(2).parentLine()).isEqualTo(r.topics().get(1).sourceLine());
        assertThat(r.topics().get(2).kanjiSection()).isTrue();

        var v = r.vocabularies();
        assertThat(v).hasSize(4);
        assertThat(v.get(0).japaneseText()).isEqualTo("私（わたし）");
        assertThat(v.get(0).reading()).isEqualTo("わたし");
        assertThat(v.get(1).reading()).isNull();            // nguồn không ghi → không suy đoán
        assertThat(v.get(2).reading()).isNull();            // ngoặc giữa từ → không xác định duy nhất
        assertThat(v.get(3).hasKanji()).isFalse();
        assertThat(r.readingNotProvided()).isEqualTo(1);
        assertThat(r.readingAmbiguous()).isEqualTo(1);

        assertThat(r.kanji()).singleElement().satisfies(k -> {
            assertThat(k.character()).isEqualTo("駅");
            assertThat(k.kunyomi()).isEqualTo("-");
        });
    }

    @Test
    void missingFieldIsReportedWithLineAndRowNotParsed() throws IOException {
        String md = """
                ## 1. Chào hỏi
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | すみません |  | Xin lỗi |
                | あの | ano |
                """;
        ParsedVocabularyFile r = parse(md);

        assertThat(r.vocabularies()).isEmpty();
        assertThat(r.errors()).hasSize(2);
        assertThat(r.errors().get(0).line()).isEqualTo(4);
        assertThat(r.errors().get(0).message()).contains("Romaji");
        assertThat(r.errors().get(1).line()).isEqualTo(5);
        assertThat(r.errors().get(1).message()).contains("Số cột");
        // Thông báo lỗi không chứa nội dung bản ghi
        assertThat(r.errors()).noneMatch(e -> e.message().contains("すみません"));
    }

    @Test
    void invalidTopicIsReported() throws IOException {
        String md = """
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | あの | ano | À |
                ### Không có chủ đề cha
                ## 5. Địa điểm
                | Kanji | Hán Việt | Ý nghĩa | On | Kun |
                | --- | --- | --- | --- | --- |
                | 駅 | DỊCH | Ga tàu | えき | - |
                """;
        ParsedVocabularyFile r = parse(md);

        assertThat(r.errors()).extracting(e -> e.line()).containsExactly(3, 4, 8);
    }

    @Test
    void duplicatesInSameTopicAreSkippedButKeptAcrossTopics() throws IOException {
        String md = """
                ## 2. Bản thân
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | 私（わたし） | watashi | Tôi |
                | 私（わたし） | watashi | Tôi |
                ## 13. Kanji
                ### Kanji A2-1 Lesson 1
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | 私（わたし） | watashi | Tôi |
                """;
        ParsedVocabularyFile r = parse(md);

        assertThat(r.errors()).isEmpty();
        assertThat(r.rowsRead()).isEqualTo(3);
        assertThat(r.skippedDuplicateInFile()).isEqualTo(1);
        assertThat(r.vocabularies()).extracting(v -> v.sourceLine()).containsExactly(4, 10);
    }

    @Test
    void realSourceFileParsesWithoutErrors() throws IOException {
        Path source = Path.of("tu-vung-tieng-nhat-da-sap-xep.md");
        ParsedVocabularyFile r;
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            r = parser.parse(reader);
        }

        // 14 chủ đề chính + 63 chủ đề con.
        // 1547 hàng từ vựng + 126 Kanji = 1673 hàng dữ liệu (bao gồm cả dòng 419 và 1573 có nghĩa là 'Tiếng Nhật').
        assertThat(r.errors()).isEmpty();
        assertThat(r.topics()).hasSize(14 + 63);
        assertThat(r.rowsRead()).isEqualTo(1547 + 126);
        assertThat(r.kanji()).hasSize(126);
        assertThat(r.vocabularies().size() + r.skippedDuplicateInFile()).isEqualTo(1547);
    }
}
