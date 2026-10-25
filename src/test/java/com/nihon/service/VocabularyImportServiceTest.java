package com.nihon.service;

import com.nihon.dto.ImportReport;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.io.StringReader;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({VocabularyImportService.class, VocabularyFileParser.class})
class VocabularyImportServiceTest {

    private static final String VALID = """
            ## 2. Bản thân
            | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
            | --- | --- | --- |
            | 私（わたし） | watashi | Tôi |
            | 1歳 | issai | Một tuổi |
            ## 13. Kanji
            ### Kanji Lesson 5
            | Kanji | Hán Việt | Ý nghĩa | On | Kun |
            | --- | --- | --- | --- | --- |
            | 駅 | DỊCH | Ga tàu | えき | - |
            """;

    @Autowired private VocabularyImportService service;
    @Autowired private TopicRepository topicRepository;
    @Autowired private VocabularyRepository vocabularyRepository;
    @Autowired private KanjiEntryRepository kanjiEntryRepository;

    @Test
    void validDataIsInsertedVerbatim() {
        ImportReport r = service.importFrom(new StringReader(VALID));

        assertThat(r.isAborted()).isFalse();
        assertThat(r.getRowsRead()).isEqualTo(3);
        assertThat(r.getTopicsInserted()).isEqualTo(3);
        assertThat(r.getVocabularyInserted()).isEqualTo(2);
        assertThat(r.getKanjiInserted()).isEqualTo(1);
        assertThat(r.getErrorCount()).isZero();

        var v = vocabularyRepository.findBySourceLine(5).orElseThrow();
        assertThat(v.getJapaneseText()).isEqualTo("1歳");
        assertThat(v.getReading()).isNull();
        assertThat(kanjiEntryRepository.findByCharacter("駅").orElseThrow().getKunyomi()).isEqualTo("-");
        assertThat(topicRepository.findBySourceLine(7).orElseThrow().getParent().getSourceLine()).isEqualTo(6);
    }

    @Test
    void reimportIsIdempotent() {
        service.importFrom(new StringReader(VALID));
        ImportReport second = service.importFrom(new StringReader(VALID));

        assertThat(second.isAborted()).isFalse();
        assertThat(second.getInserted()).isZero();
        assertThat(second.getTopicsInserted()).isZero();
        assertThat(second.getSkippedAlreadyImported()).isEqualTo(3);
        assertThat(vocabularyRepository.count()).isEqualTo(2);
        assertThat(topicRepository.count()).isEqualTo(3);
    }

    @Test
    void missingFieldAbortsWithoutWritingAnything() {
        String md = """
                ## 1. Chào hỏi
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | あの | ano | À, ờ |
                | すみません | sumimasen |  |
                """;
        ImportReport r = service.importFrom(new StringReader(md));

        assertThat(r.isAborted()).isTrue();
        assertThat(r.getErrorCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).line()).isEqualTo(5);
        assertThat(topicRepository.count()).isZero();
        assertThat(vocabularyRepository.count()).isZero();
    }

    @Test
    void duplicateRowInFileIsSkipped() {
        String md = """
                ## 2. Bản thân
                | Tiếng Nhật | Romaji | Nghĩa tiếng Việt |
                | --- | --- | --- |
                | 私（わたし） | watashi | Tôi |
                | 私（わたし） | watashi | Tôi |
                """;
        ImportReport r = service.importFrom(new StringReader(md));

        assertThat(r.getRowsRead()).isEqualTo(2);
        assertThat(r.getVocabularyInserted()).isEqualTo(1);
        assertThat(r.getSkippedDuplicateInFile()).isEqualTo(1);
        assertThat(vocabularyRepository.count()).isEqualTo(1);
    }

    @Test
    void conflictingContentAtSameSourceLineAbortsWithoutChanges() {
        service.importFrom(new StringReader(VALID));
        String changed = VALID.replace("| 私（わたし） | watashi | Tôi |", "| 僕（ぼく） | boku | Tôi (nam) |");

        ImportReport r = service.importFrom(new StringReader(changed));

        assertThat(r.isAborted()).isTrue();
        assertThat(r.getErrors()).extracting(e -> e.line()).containsExactly(4);
        assertThat(vocabularyRepository.findBySourceLine(4).orElseThrow().getJapaneseText()).isEqualTo("私（わたし）");
    }

    @Test
    void realSourceFileImportsAndReimportsIdempotently() {
        Path source = Path.of("tu-vung-tieng-nhat-da-sap-xep.md");

        ImportReport first = service.importFrom(source);
        assertThat(first.isAborted()).isFalse();
        assertThat(first.getRowsRead()).isEqualTo(1673);
        assertThat(first.getKanjiInserted()).isEqualTo(126);
        assertThat(first.getInserted() + first.getSkippedDuplicateInFile()).isEqualTo(1673);

        ImportReport second = service.importFrom(source);
        assertThat(second.getInserted()).isZero();
        assertThat(second.getSkippedAlreadyImported()).isEqualTo(first.getInserted());
    }
}
