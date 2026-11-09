package com.nihon.validation;

import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.VocabularyFileParser;
import com.nihon.service.excel.KanjiSheetParser;
import com.nihon.service.excel.WordSheetParser;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tự động bằng Reflection đối chiếu hằng số độ dài tối đa trong các Parser
 * và KanjiLimits với độ dài cột (@Column(length = ...)) trên các Entity JPA.
 * Nếu bất kỳ hằng số nào lệch với @Column.length của Entity, test sẽ thất bại ngay lập tức.
 */
class FieldLimitsConsistencyTest {

    static Stream<Arguments> parserConstantToEntityColumnMappings() {
        return Stream.of(
                // 1. Bảng topic (Topic.java)
                Arguments.of(KanjiLimits.class, "MAX_TOPIC_LENGTH", Topic.class, "name"),
                Arguments.of(WordSheetParser.class, "MAX_TOPIC_LENGTH", Topic.class, "name"),
                Arguments.of(KanjiSheetParser.class, "MAX_TOPIC_LENGTH", Topic.class, "name"),
                Arguments.of(VocabularyFileParser.class, "MAX_TOPIC", Topic.class, "name"),

                // 2. Bảng vocabulary (Vocabulary.java)
                Arguments.of(KanjiLimits.class, "MAX_JAPANESE_LENGTH", Vocabulary.class, "japaneseText"),
                Arguments.of(WordSheetParser.class, "MAX_JAPANESE_LENGTH", Vocabulary.class, "japaneseText"),
                Arguments.of(KanjiSheetParser.class, "MAX_WORD_JAPANESE_LENGTH", Vocabulary.class, "japaneseText"),
                Arguments.of(VocabularyFileParser.class, "MAX_JAPANESE", Vocabulary.class, "japaneseText"),

                Arguments.of(KanjiLimits.class, "MAX_READING_LENGTH", Vocabulary.class, "reading"),
                Arguments.of(WordSheetParser.class, "MAX_READING_LENGTH", Vocabulary.class, "reading"),
                Arguments.of(VocabularyFileParser.class, "MAX_READING", Vocabulary.class, "reading"),

                Arguments.of(KanjiLimits.class, "MAX_ROMAJI_LENGTH", Vocabulary.class, "romaji"),
                Arguments.of(WordSheetParser.class, "MAX_ROMAJI_LENGTH", Vocabulary.class, "romaji"),
                Arguments.of(KanjiSheetParser.class, "MAX_WORD_ROMAJI_LENGTH", Vocabulary.class, "romaji"),
                Arguments.of(VocabularyFileParser.class, "MAX_ROMAJI", Vocabulary.class, "romaji"),

                Arguments.of(KanjiLimits.class, "MAX_VOCAB_MEANING_LENGTH", Vocabulary.class, "meaningVi"),
                Arguments.of(WordSheetParser.class, "MAX_MEANING_LENGTH", Vocabulary.class, "meaningVi"),
                Arguments.of(KanjiSheetParser.class, "MAX_WORD_MEANING_LENGTH", Vocabulary.class, "meaningVi"),
                Arguments.of(VocabularyFileParser.class, "MAX_MEANING_VOCAB", Vocabulary.class, "meaningVi"),

                // 3. Bảng kanji_entry (KanjiEntry.java)
                Arguments.of(KanjiLimits.class, "MAX_KANJI_CHAR_LENGTH", KanjiEntry.class, "character"),
                Arguments.of(KanjiSheetParser.class, "MAX_KANJI_CHAR_LENGTH", KanjiEntry.class, "character"),
                Arguments.of(VocabularyFileParser.class, "MAX_KANJI_CHAR", KanjiEntry.class, "character"),

                Arguments.of(KanjiLimits.class, "MAX_HAN_VIET_LENGTH", KanjiEntry.class, "hanViet"),
                Arguments.of(KanjiSheetParser.class, "MAX_HAN_VIET_LENGTH", KanjiEntry.class, "hanViet"),
                Arguments.of(VocabularyFileParser.class, "MAX_HAN_VIET", KanjiEntry.class, "hanViet"),

                Arguments.of(KanjiLimits.class, "MAX_KANJI_MEANING_LENGTH", KanjiEntry.class, "meaningVi"),
                Arguments.of(KanjiSheetParser.class, "MAX_MEANING_LENGTH", KanjiEntry.class, "meaningVi"),
                Arguments.of(VocabularyFileParser.class, "MAX_MEANING_KANJI", KanjiEntry.class, "meaningVi"),

                Arguments.of(KanjiLimits.class, "MAX_ONYOMI_LENGTH", KanjiEntry.class, "onyomi"),
                Arguments.of(KanjiLimits.class, "MAX_ON_KUN_LENGTH", KanjiEntry.class, "onyomi"),
                Arguments.of(KanjiSheetParser.class, "MAX_ON_KUN_LENGTH", KanjiEntry.class, "onyomi"),
                Arguments.of(VocabularyFileParser.class, "MAX_ON_KUN", KanjiEntry.class, "onyomi"),

                Arguments.of(KanjiLimits.class, "MAX_KUNYOMI_LENGTH", KanjiEntry.class, "kunyomi"),
                Arguments.of(KanjiLimits.class, "MAX_ON_KUN_LENGTH", KanjiEntry.class, "kunyomi"),
                Arguments.of(KanjiSheetParser.class, "MAX_ON_KUN_LENGTH", KanjiEntry.class, "kunyomi"),
                Arguments.of(VocabularyFileParser.class, "MAX_ON_KUN", KanjiEntry.class, "kunyomi")
        );
    }

    @ParameterizedTest(name = "{0}.{1} phải bằng @{2}.{3} @Column(length)")
    @MethodSource("parserConstantToEntityColumnMappings")
    @DisplayName("Hằng số giới hạn trong Parser/KanjiLimits khớp tuyệt đối với @Column(length) của Entity")
    void parserConstantMatchesEntityColumnLength(Class<?> constantHolderClass,
                                                 String constantFieldName,
                                                 Class<?> entityClass,
                                                 String entityFieldName) throws Exception {
        Field constantField = constantHolderClass.getDeclaredField(constantFieldName);
        constantField.setAccessible(true);
        int parserMaxLen = constantField.getInt(null);

        Field entityField = entityClass.getDeclaredField(entityFieldName);
        Column columnAnnotation = entityField.getAnnotation(Column.class);
        assertThat(columnAnnotation)
                .as("Trường %s.%s phải có @Column", entityClass.getSimpleName(), entityFieldName)
                .isNotNull();

        int columnLen = columnAnnotation.length();

        assertThat(parserMaxLen)
                .as("Hằng số %s.%s (%d) bị lệch với @Column(length = %d) của %s.%s",
                        constantHolderClass.getSimpleName(), constantFieldName, parserMaxLen,
                        columnLen, entityClass.getSimpleName(), entityFieldName)
                .isEqualTo(columnLen);
    }
}

