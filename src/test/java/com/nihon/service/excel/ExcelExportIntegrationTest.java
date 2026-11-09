package com.nihon.service.excel;

import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.KanjiService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử chức năng xuất dữ liệu Excel (GET /import/export - ImportService.exportToExcel):
 * 1) Export rồi truyền thẳng file đó vào import trên DB rỗng: dữ liệu khớp.
 * 2) Ô có nội dung bắt đầu bằng "=1+1" được ghi dạng chuỗi có tiền tố '.
 * 3) Header và vùng merge đúng.
 * 4) Vượt giới hạn 50000 hàng báo lỗi rõ.
 */
@SpringBootTest
@Transactional
class ExcelExportIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private KanjiService kanjiService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    private Topic createTopic(String name, int sortOrder, Integer sourceLine, boolean kanjiSection) {
        Topic t = new Topic();
        t.setName(name);
        t.setSortOrder(sortOrder);
        t.setSourceLine(sourceLine);
        t.setKanjiSection(kanjiSection);
        return t;
    }

    private Vocabulary createVocab(Topic topic, String japaneseText, String reading, String romaji,
                                   String meaningVi, boolean hasKanji, Integer sourceLine) {
        Vocabulary v = new Vocabulary();
        v.setTopic(topic);
        v.setJapaneseText(japaneseText);
        v.setReading(reading);
        v.setRomaji(romaji);
        v.setMeaningVi(meaningVi);
        v.setHasKanji(hasKanji);
        v.setSourceLine(sourceLine);
        return v;
    }

    private KanjiEntry createKanji(Topic topic, String character, String hanViet, String meaningVi,
                                   String onyomi, String kunyomi, int sourceLine) {
        KanjiEntry k = new KanjiEntry();
        k.setTopic(topic);
        k.setCharacter(character);
        k.setHanViet(hanViet);
        k.setMeaningVi(meaningVi);
        k.setOnyomi(onyomi);
        k.setKunyomi(kunyomi);
        k.setSourceLine(sourceLine);
        return k;
    }

    private void clearDatabase() {
        kanjiEntryRepository.deleteAllInBatch();
        vocabularyRepository.deleteAllInBatch();
        List<Topic> allTopics = topicRepository.findAll();
        for (Topic t : allTopics) {
            if (t.getParent() != null) {
                t.setParent(null);
            }
        }
        topicRepository.saveAllAndFlush(allTopics);
        topicRepository.deleteAllInBatch();
        topicRepository.flush();
    }

    @Test
    @DisplayName("1. Export rồi truyền thẳng file đó vào import trên DB rỗng: dữ liệu khớp hoàn toàn")
    void exportThenImportIntoEmptyDatabase_dataMatches() throws IOException {
        clearDatabase();

        // 1. Khởi tạo dữ liệu mẫu gồm chủ đề từ vựng thường và chủ đề Kanji
        Topic wordTopic1 = topicRepository.save(createTopic("Chủ đề Giao tiếp", 1, 1, false));
        Topic wordTopic2 = topicRepository.save(createTopic("Chủ đề Trường học", 2, 2, false));
        Topic kanjiTopic = topicRepository.save(createTopic("Kanji Bài 1", 3, 3, true));

        // Từ vựng thuộc chủ đề thường
        vocabularyRepository.save(createVocab(wordTopic1, "先生（せんせい）", "せんせい", "sensei", "Giáo viên", true, 10));
        vocabularyRepository.save(createVocab(wordTopic1, "学生（がくせい）", "がくせい", "gakusei", "Học sinh", true, 11));
        vocabularyRepository.save(createVocab(wordTopic2, "図書館（としょかん）", "としょかん", "toshokan", "Thư viện", true, 12));

        // Kanji và các từ cần nhớ thuộc chủ đề Kanji
        kanjiEntryRepository.save(createKanji(kanjiTopic, "日", "NHẬT", "Mặt trời, ngày", "ニチ", "ひ", 100));
        kanjiEntryRepository.save(createKanji(kanjiTopic, "本", "BẢN", "Sách, gốc", "ホン", "もと", 101));

        vocabularyRepository.save(createVocab(kanjiTopic, "日本（にほん）", "にほん", "nihon", "Nhật Bản", true, 20));
        vocabularyRepository.save(createVocab(kanjiTopic, "日曜日（にちようび）", "にちようび", "nichiyoubi", "Chủ nhật", true, 21));
        vocabularyRepository.save(createVocab(kanjiTopic, "絵本（えほん）", "えほん", "ehon", "Sách tranh", true, 22));
        topicRepository.flush();
        vocabularyRepository.flush();
        kanjiEntryRepository.flush();

        long expectedTopicCount = topicRepository.count();
        long expectedVocabCount = vocabularyRepository.count();
        long expectedKanjiCount = kanjiEntryRepository.count();
        int expectedWordsForNichi = kanjiService.getWordsForKanji(kanjiTopic.getId(), "日").size();
        int expectedWordsForHon = kanjiService.getWordsForKanji(kanjiTopic.getId(), "本").size();

        // 2. Xuất dữ liệu ra mảng byte .xlsx
        byte[] exportedBytes = importService.exportToExcel();
        assertThat(exportedBytes).isNotEmpty();

        // 3. Xóa sạch cơ sở dữ liệu về trạng thái rỗng
        clearDatabase();
        assertThat(topicRepository.count()).isZero();
        assertThat(vocabularyRepository.count()).isZero();
        assertThat(kanjiEntryRepository.count()).isZero();

        // 4. Truyền thẳng file vừa xuất vào hàm commit import trên DB rỗng
        ImportCommitResult result = importService.commit(
                exportedBytes,
                "nihon-export-roundtrip.xlsx",
                ImportMode.SKIP_DUPLICATES,
                "127.0.0.1"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();

        // 5. Đối chiếu dữ liệu sau khi import lại
        assertThat(topicRepository.count()).isEqualTo(expectedTopicCount);
        assertThat(vocabularyRepository.count()).isEqualTo(expectedVocabCount);
        assertThat(kanjiEntryRepository.count()).isEqualTo(expectedKanjiCount);

        Topic reimportedWordTopic1 = topicRepository.findByNameIgnoreCase("Chủ đề Giao tiếp").orElseThrow();
        assertThat(reimportedWordTopic1.isKanjiSection()).isFalse();
        List<Vocabulary> topic1Words = vocabularyRepository.findByTopicIdOrderBySourceLineAsc(reimportedWordTopic1.getId());
        assertThat(topic1Words).hasSize(2);
        assertThat(topic1Words).extracting(Vocabulary::getJapaneseText)
                .containsExactlyInAnyOrder("先生（せんせい）", "学生（がくせい）");

        Topic reimportedKanjiTopic = topicRepository.findByNameIgnoreCase("Kanji Bài 1").orElseThrow();
        assertThat(reimportedKanjiTopic.isKanjiSection()).isTrue();

        KanjiEntry reimportedNichi = kanjiEntryRepository.findByCharacter("日").orElseThrow();
        assertThat(reimportedNichi.getHanViet()).isEqualTo("NHẬT");
        assertThat(reimportedNichi.getMeaningVi()).isEqualTo("Mặt trời, ngày");
        assertThat(reimportedNichi.getOnyomi()).isEqualTo("ニチ");
        assertThat(reimportedNichi.getKunyomi()).isEqualTo("ひ");

        KanjiEntry reimportedHon = kanjiEntryRepository.findByCharacter("本").orElseThrow();
        assertThat(reimportedHon.getHanViet()).isEqualTo("BẢN");
        assertThat(reimportedHon.getMeaningVi()).isEqualTo("Sách, gốc");

        assertThat(kanjiService.getWordsForKanji(reimportedKanjiTopic.getId(), "日")).hasSize(expectedWordsForNichi);
        assertThat(kanjiService.getWordsForKanji(reimportedKanjiTopic.getId(), "本")).hasSize(expectedWordsForHon);
    }

    @Test
    @DisplayName("2. Chống Formula Injection: Ô bắt đầu bằng '=1+1' (hoặc +, -, @, tab, newline) được ghi dạng chuỗi có tiền tố '")
    void exportToExcel_formulaInjectionCells_areWrittenAsStringWithSingleQuotePrefix() throws IOException {
        clearDatabase();

        Topic topic = topicRepository.save(createTopic("=SUM(A1:A10)", 1, 1, false));
        vocabularyRepository.save(createVocab(
                topic,
                "猫（ねこ）",
                "ねこ",
                "+cmd|' /C calc'!A0",
                "=1+1",
                true,
                1
        ));
        topicRepository.flush();
        vocabularyRepository.flush();

        byte[] exportedBytes = importService.exportToExcel();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exportedBytes))) {
            Sheet wordSheet = wb.getSheet(WordSheetParser.TARGET_SHEET_NAME);
            assertThat(wordSheet).isNotNull();

            Row dataRow = wordSheet.getRow(1);
            assertThat(dataRow).isNotNull();

            // Cột A (Topic): '=SUM(A1:A10)
            Cell topicCell = dataRow.getCell(0);
            assertThat(topicCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(topicCell.getStringCellValue()).isEqualTo("'=SUM(A1:A10)");

            // Cột C (Romaji): '+cmd|' /C calc'!A0
            Cell romajiCell = dataRow.getCell(2);
            assertThat(romajiCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(romajiCell.getStringCellValue()).isEqualTo("'+cmd|' /C calc'!A0");

            // Cột D (Nghĩa): '=1+1
            Cell meaningCell = dataRow.getCell(3);
            assertThat(meaningCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(meaningCell.getStringCellValue()).isEqualTo("'=1+1");
        }

        // Kiểm tra thêm các ký tự -, @, tab, xuống dòng qua hàm sanitizeFormulaInjection
        assertThat(ImportService.sanitizeFormulaInjection("-1+1")).isEqualTo("'-1+1");
        assertThat(ImportService.sanitizeFormulaInjection("@SUM(1,2)")).isEqualTo("'@SUM(1,2)");
        assertThat(ImportService.sanitizeFormulaInjection("\t=1+1")).isEqualTo("'\t=1+1");
        assertThat(ImportService.sanitizeFormulaInjection("\n=1+1")).isEqualTo("'\n=1+1");
        assertThat(ImportService.sanitizeFormulaInjection("\r=1+1")).isEqualTo("'\r=1+1");
    }

    @Test
    @DisplayName("3. Header của hai sheet 'New Word', 'Kanji' và vùng merge G1:I1 đúng cấu trúc import")
    void exportToExcel_headersAndMergedRegion_matchParserSpec() throws IOException {
        clearDatabase();

        Topic wordTopic = topicRepository.save(createTopic("Chủ đề 1", 1, 1, false));
        vocabularyRepository.save(createVocab(wordTopic, "水（みず）", "みず", "mizu", "Nước", true, 1));
        vocabularyRepository.save(createVocab(wordTopic, "火（ひ）", "ひ", "hi", "Lửa", true, 2));

        Topic kanjiTopic = topicRepository.save(createTopic("Kanji N5", 2, 2, true));
        kanjiEntryRepository.save(createKanji(kanjiTopic, "水", "THỦY", "Nước", "スイ", "みず", 10));
        vocabularyRepository.save(createVocab(kanjiTopic, "水曜日（すいようび）", "すいようび", "suiyoubi", "Thứ tư", true, 3));
        topicRepository.flush();
        vocabularyRepository.flush();
        kanjiEntryRepository.flush();

        byte[] exportedBytes = importService.exportToExcel();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exportedBytes))) {
            // Kiểm tra Sheet "New Word"
            Sheet wordSheet = wb.getSheet("New Word");
            assertThat(wordSheet).isNotNull();

            Row wordHeader = wordSheet.getRow(0);
            assertThat(wordHeader.getCell(0).getStringCellValue()).isEqualTo("Topic");
            assertThat(wordHeader.getCell(1).getStringCellValue()).isEqualTo("Tiếng Nhật");
            assertThat(wordHeader.getCell(2).getStringCellValue()).isEqualTo("Romaji");
            assertThat(wordHeader.getCell(3).getStringCellValue()).isEqualTo("Nghĩa tiếng Việt");

            // Hàng đầu của chủ đề có tên chủ đề ở cột A, hàng sau để trống cột A
            Row wordRow1 = wordSheet.getRow(1);
            assertThat(wordRow1.getCell(0).getStringCellValue()).isEqualTo("Chủ đề 1");
            assertThat(wordRow1.getCell(1).getStringCellValue()).isEqualTo("水（みず）");

            Row wordRow2 = wordSheet.getRow(2);
            assertThat(wordRow2.getCell(0)).isNull();
            assertThat(wordRow2.getCell(1).getStringCellValue()).isEqualTo("火（ひ）");

            // Kiểm tra Sheet "Kanji"
            Sheet kanjiSheet = wb.getSheet("Kanji");
            assertThat(kanjiSheet).isNotNull();

            Row kanjiHeader1 = kanjiSheet.getRow(0);
            assertThat(kanjiHeader1.getCell(0).getStringCellValue()).isEqualTo("Topic");
            assertThat(kanjiHeader1.getCell(1).getStringCellValue()).isEqualTo("Kanji");
            assertThat(kanjiHeader1.getCell(2).getStringCellValue()).isEqualTo("Hán Việt");
            assertThat(kanjiHeader1.getCell(3).getStringCellValue()).isEqualTo("Ý nghĩa");
            assertThat(kanjiHeader1.getCell(4).getStringCellValue()).isEqualTo("On");
            assertThat(kanjiHeader1.getCell(5).getStringCellValue()).isEqualTo("Kun");
            assertThat(kanjiHeader1.getCell(6).getStringCellValue()).isEqualTo("Từ cần nhớ");

            Row kanjiHeader2 = kanjiSheet.getRow(1);
            assertThat(kanjiHeader2.getCell(6).getStringCellValue()).isEqualTo("Tiếng Nhật");
            assertThat(kanjiHeader2.getCell(7).getStringCellValue()).isEqualTo("Romaji");
            assertThat(kanjiHeader2.getCell(8).getStringCellValue()).isEqualTo("Nghĩa tiếng Việt");

            // Kiểm tra vùng merge G1:I1 (row 0..0, col 6..8)
            boolean hasG1I1Merge = false;
            for (int i = 0; i < kanjiSheet.getNumMergedRegions(); i++) {
                CellRangeAddress region = kanjiSheet.getMergedRegion(i);
                if (region.getFirstRow() == 0 && region.getLastRow() == 0
                        && region.getFirstColumn() == 6 && region.getLastColumn() == 8) {
                    hasG1I1Merge = true;
                    break;
                }
            }
            assertThat(hasG1I1Merge).isTrue();

            // Hàng Kanji (row 2) có cột B là Kanji, hàng tiếp theo (row 3) cột B trống và cột G, H, I là từ cần nhớ
            Row kanjiDataRow = kanjiSheet.getRow(2);
            assertThat(kanjiDataRow.getCell(0).getStringCellValue()).isEqualTo("Kanji N5");
            assertThat(kanjiDataRow.getCell(1).getStringCellValue()).isEqualTo("水");
            assertThat(kanjiDataRow.getCell(6)).isNull();

            Row kanjiWordRow = kanjiSheet.getRow(3);
            assertThat(kanjiWordRow.getCell(1)).isNull();
            assertThat(kanjiWordRow.getCell(6).getStringCellValue()).isEqualTo("水曜日（すいようび）");
            assertThat(kanjiWordRow.getCell(7).getStringCellValue()).isEqualTo("suiyoubi");
            assertThat(kanjiWordRow.getCell(8).getStringCellValue()).isEqualTo("Thứ tư");
        }
    }

    @Test
    @DisplayName("4. Vượt giới hạn 50000 hàng mỗi sheet báo lỗi rõ ràng")
    void exportToExcel_exceeding50000RowsLimit_throwsClearError() {
        TopicRepository mockTopicRepo = mock(TopicRepository.class);
        VocabularyRepository mockVocabRepo = mock(VocabularyRepository.class);
        KanjiEntryRepository mockKanjiRepo = mock(KanjiEntryRepository.class);
        ImportAuditService mockAuditService = mock(ImportAuditService.class);

        ImportService serviceWithMocks = new ImportService(
                mock(ExcelUploadValidator.class),
                new WordSheetParser(),
                new KanjiSheetParser(),
                mockTopicRepo,
                mockVocabRepo,
                mockKanjiRepo,
                mockAuditService
        );

        Topic topic = createTopic("Chủ đề lớn", 1, 1, false);
        topic.setId(1L);
        when(mockTopicRepo.findAllByOrderBySortOrderAsc()).thenReturn(List.of(topic));

        // 1 header row + 50,000 data rows = 50,001 rows (> 50,000)
        List<Vocabulary> hugeVocabList = new ArrayList<>(ImportService.MAX_EXPORT_ROWS_PER_SHEET);
        Vocabulary sampleVocab = createVocab(topic, "あ", null, "a", "A", false, 1);
        for (int i = 0; i < ImportService.MAX_EXPORT_ROWS_PER_SHEET; i++) {
            hugeVocabList.add(sampleVocab);
        }
        when(mockVocabRepo.findByTopicIdOrderBySourceLineAsc(1L)).thenReturn(hugeVocabList);

        assertThatThrownBy(serviceWithMocks::exportToExcel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("50000")
                .hasMessageContaining("New Word");
    }
}
