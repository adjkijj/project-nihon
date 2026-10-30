package com.nihon.service;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.KanjiWord;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.excel.ImportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KanjiWordUnifiedVocabularyTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private KanjiWordMigrationService migrationService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private KanjiWordRepository kanjiWordRepository;

    @Autowired
    private VocabularyService vocabularyService;

    private byte[] createTestWorkbook(String topicName, String kanjiChar, String hanViet,
                                      String onyomi, String kunyomi,
                                      List<String[]> words) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet1 = workbook.createSheet("New Word");
            Row h1 = sheet1.createRow(0);
            h1.createCell(0).setCellValue("Topic");
            h1.createCell(1).setCellValue("Tiếng Nhật");
            h1.createCell(2).setCellValue("Romaji");
            h1.createCell(3).setCellValue("Nghĩa tiếng Việt");

            Sheet sheet2 = workbook.createSheet("Kanji");
            Row r0 = sheet2.createRow(0);
            r0.createCell(0).setCellValue("Topic");
            r0.createCell(1).setCellValue("Kanji");
            r0.createCell(2).setCellValue("Hán Việt");
            r0.createCell(3).setCellValue("Ý nghĩa");
            r0.createCell(4).setCellValue("On");
            r0.createCell(5).setCellValue("Kun");
            r0.createCell(6).setCellValue("Word");
            sheet2.addMergedRegion(new CellRangeAddress(0, 0, 6, 8));

            Row r1 = sheet2.createRow(1);
            r1.createCell(6).setCellValue("Tiếng Nhật");
            r1.createCell(7).setCellValue("Romaji");
            r1.createCell(8).setCellValue("Nghĩa tiếng Việt");

            int rowIndex = 2;
            for (int i = 0; i < words.size(); i++) {
                Row r = sheet2.createRow(rowIndex++);
                if (i == 0) {
                    r.createCell(0).setCellValue(topicName);
                    r.createCell(1).setCellValue(kanjiChar);
                    r.createCell(2).setCellValue(hanViet);
                    r.createCell(3).setCellValue("Ý nghĩa test");
                    r.createCell(4).setCellValue(onyomi);
                    r.createCell(5).setCellValue(kunyomi);
                }
                String[] w = words.get(i);
                r.createCell(6).setCellValue(w[0]);
                if (w.length > 1 && w[1] != null) r.createCell(7).setCellValue(w[1]);
                if (w.length > 2 && w[2] != null) r.createCell(8).setCellValue(w[2]);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("1. Import Kanji có từ: từ xuất hiện trong bảng từ vựng chung, đúng chủ đề, đúng Cách đọc tách từ ngoặc")
    void testImportKanjiWithWords_SavesToVocabularyWithCorrectReading() throws IOException {
        String topicName = "Kanji Test Bài 1";
        byte[] bytes = createTestWorkbook(
                topicName, "酒", "TỬU", "シュ", "さけ",
                List.of(
                        new String[]{"お酒（おさけ）", "osake", "Rượu"},
                        new String[]{"地味（な）（じみ）", "jimi", "Đơn giản"}
                )
        );

        MockMultipartFile file = new MockMultipartFile("file", "test1.xlsx", "application/octet-stream", bytes);
        ImportCommitResult result = importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester");
        assertThat(result.success()).isTrue();

        Topic topic = topicRepository.findByNameIgnoreCase(topicName).orElseThrow();
        Optional<Vocabulary> v1 = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), "お酒（おさけ）");
        assertThat(v1).isPresent();
        assertThat(v1.get().getReading()).isEqualTo("おさけ");
        assertThat(v1.get().getRomaji()).isEqualTo("osake");
        assertThat(v1.get().getMeaningVi()).isEqualTo("Rượu");

        Optional<Vocabulary> v2 = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), "地味（な）（じみ）");
        assertThat(v2).isPresent();
        assertThat(v2.get().getReading()).isEqualTo("じみ"); // Ngoặc cuối
    }

    @Test
    @DisplayName("2. Từ đã có sẵn ở chủ đề khác: không tạo trùng trong cùng chủ đề, thêm bản ghi độc lập ở chủ đề mới")
    void testWordAlreadyExistsInAnotherTopic_CreatesRowInNewTopic() throws IOException {
        String topicA = "Chủ đề A";
        String topicB = "Chủ đề B";

        // Thêm vào chủ đề A
        byte[] bytesA = createTestWorkbook(
                topicA, "店", "ĐIẾM", "テン", "みせ",
                java.util.Collections.singletonList(new String[]{"店（みせ）", "mise", "Cửa hàng"})
        );
        importService.commit(new MockMultipartFile("file", "testA.xlsx", "application/octet-stream", bytesA), ImportMode.SKIP_DUPLICATES, "tester");

        Topic tA = topicRepository.findByNameIgnoreCase(topicA).orElseThrow();
        assertThat(vocabularyRepository.findByTopicIdAndJapaneseText(tA.getId(), "店（みせ）")).isPresent();

        // Thêm từ đó vào chủ đề B
        byte[] bytesB = createTestWorkbook(
                topicB, "店", "ĐIẾM", "テン", "みせ",
                java.util.Collections.singletonList(new String[]{"店（みせ）", "mise", "Cửa hàng"})
        );
        importService.commit(new MockMultipartFile("file", "testB.xlsx", "application/octet-stream", bytesB), ImportMode.SKIP_DUPLICATES, "tester");

        Topic tB = topicRepository.findByNameIgnoreCase(topicB).orElseThrow();
        assertThat(vocabularyRepository.findByTopicIdAndJapaneseText(tB.getId(), "店（みせ）")).isPresent();

        // Hai bản ghi thuộc 2 chủ đề khác nhau
        assertThat(tA.getId()).isNotEqualTo(tB.getId());
    }

    @Test
    @DisplayName("3. Import lại cùng file: không tạo bản ghi trùng, báo cáo trùng đúng số lượng")
    void testReimportSameFile_NoDuplicates_ReportMatches() throws IOException {
        String topicName = "Kanji Trùng Lặp Test";
        byte[] bytes = createTestWorkbook(
                topicName, "茶", "TRÀ", "チャ", "おちゃ",
                List.of(
                        new String[]{"お茶（おちゃ）", "ocha", "Trà"},
                        new String[]{"喫茶店（きっさてん）", "kissaten", "Quán cà phê"}
                )
        );
        MockMultipartFile file = new MockMultipartFile("file", "test3.xlsx", "application/octet-stream", bytes);

        // Lần 1
        ImportCommitResult res1 = importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester");
        assertThat(res1.wordsAdded()).isEqualTo(2);

        // Preview lần 2
        ImportPreviewReport preview = importService.preview(file);
        assertThat(preview.newWordsCount()).isEqualTo(0);
        assertThat(preview.duplicateWordsCount()).isEqualTo(2);
        assertThat(preview.newKanjiWordsCount()).isEqualTo(0);
        assertThat(preview.duplicateKanjiWordsCount()).isEqualTo(2);

        // Commit lần 2
        ImportCommitResult res2 = importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester");
        assertThat(res2.wordsAdded()).isEqualTo(0);
        assertThat(res2.wordsSkipped()).isEqualTo(2);
        assertThat(res2.kanjiWordsSkipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("4. Từ không có ngoặc: Cách đọc để trống (null), không gây lỗi")
    void testWordWithoutParentheses_ReadingIsNullWithoutError() throws IOException {
        String topicName = "Kanji Không Ngoặc Test";
        byte[] bytes = createTestWorkbook(
                topicName, "野", "DÃ", "ヤ", "の",
                java.util.Collections.singletonList(new String[]{"野菜", "yasai", "Rau củ"})
        );
        MockMultipartFile file = new MockMultipartFile("file", "test4.xlsx", "application/octet-stream", bytes);

        ImportCommitResult result = importService.commit(file, ImportMode.SKIP_DUPLICATES, "tester");
        assertThat(result.success()).isTrue();

        Topic topic = topicRepository.findByNameIgnoreCase(topicName).orElseThrow();
        Vocabulary v = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), "野菜").orElseThrow();
        assertThat(v.getReading()).isNull();
        assertThat(v.getRomaji()).isEqualTo("yasai");
    }

    @Test
    @DisplayName("5. Migration trên dữ liệu có sẵn trong kanji_word: tạo đủ từ, chạy lại không trùng")
    void testMigration_MigratesKanjiWordsIdempotently() {
        Topic topic = new Topic();
        topic.setName("Chủ đề migration test");
        topic.setSortOrder(999);
        topic.setKanjiSection(true);
        topic = topicRepository.save(topic);

        KanjiEntry kanji = new KanjiEntry();
        kanji.setTopic(topic);
        kanji.setCharacter("遷");
        kanji.setHanViet("TIỂN");
        kanji.setSourceLine(9999);
        kanji = kanjiEntryRepository.save(kanji);

        KanjiWord kw = new KanjiWord();
        kw.setKanji(kanji);
        kw.setJapanese("変遷（へんせん）");
        kw.setRomaji("hensen");
        kw.setMeaningVi("Sự biến thiên");
        kw.setSortOrder(1);
        kanjiWordRepository.save(kw);

        // Chạy migration lần 1
        int migrated1 = migrationService.migrateAll();
        assertThat(migrated1).isGreaterThanOrEqualTo(1);

        Optional<Vocabulary> migratedVocab = vocabularyRepository.findByTopicIdAndJapaneseText(topic.getId(), "変遷（へんせん）");
        assertThat(migratedVocab).isPresent();
        assertThat(migratedVocab.get().getReading()).isEqualTo("へんせん");
        assertThat(migratedVocab.get().getRomaji()).isEqualTo("hensen");

        // Chạy migration lần 2: không tạo trùng, trả về 0
        int migrated2 = migrationService.migrateAll();
        assertThat(migrated2).isEqualTo(0);
    }

    @Test
    @DisplayName("6. Trang Từ vựng lọc theo chủ đề Kanji trả đúng danh sách")
    void testVocabularyPage_FilterByKanjiTopic_ReturnsCorrectList() throws IOException {
        String topicName = "Kanji Lesson Filter Test";
        byte[] bytes = createTestWorkbook(
                topicName, "物", "VẬT", "ブツ", "もの",
                List.of(
                        new String[]{"荷物（にもつ）", "nimotsu", "Hành lý"},
                        new String[]{"乗り物（のりもの）", "norimono", "Phương tiện"}
                )
        );
        importService.commit(new MockMultipartFile("file", "test6.xlsx", "application/octet-stream", bytes), ImportMode.SKIP_DUPLICATES, "tester");

        Topic topic = topicRepository.findByNameIgnoreCase(topicName).orElseThrow();
        Page<VocabularyRowDto> page = vocabularyService.search(topic.getId(), null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().stream().anyMatch(dto -> dto.getJapaneseText().equals("荷物（にもつ）"))).isTrue();
        assertThat(page.getContent().stream().anyMatch(dto -> dto.getJapaneseText().equals("乗り物（のりもの）"))).isTrue();
    }
}
