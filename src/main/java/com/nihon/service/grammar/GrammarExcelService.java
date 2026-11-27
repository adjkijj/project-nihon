package com.nihon.service.grammar;

import com.nihon.dto.grammar.GrammarImportPreviewReport;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarExample;
import com.nihon.entity.GrammarQuizChoice;
import com.nihon.entity.GrammarQuizQuestion;
import com.nihon.entity.GrammarTopic;
import com.nihon.repository.GrammarExampleRepository;
import com.nihon.repository.GrammarQuizChoiceRepository;
import com.nihon.repository.GrammarQuizQuestionRepository;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.service.excel.ExcelCellUtils;
import com.nihon.service.excel.ExcelUploadValidator;
import com.nihon.service.excel.ImportAuditService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.validation.JapaneseTextValidator;
import com.nihon.validation.KanaReadingValidator;
import com.nihon.validation.TextNormalizer;
import com.nihon.validation.VietnameseTextValidator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Service xử lý Nhập (Import) và Xuất (Export) Ngữ pháp & Trắc nghiệm qua tệp Excel (F31).
 * Hỗ trợ 2 sheet "Grammar" và "Quiz", quy trình preview 2 bước, chống Formula Injection và transaction toàn phần.
 */
@Service
public class GrammarExcelService {

    private static final Logger log = LoggerFactory.getLogger(GrammarExcelService.class);

    private final GrammarTopicRepository topicRepository;
    private final GrammarExampleRepository exampleRepository;
    private final GrammarQuizQuestionRepository questionRepository;
    private final GrammarQuizChoiceRepository choiceRepository;
    private final ExcelUploadValidator uploadValidator;
    private final ImportSessionStorage sessionStorage;
    private final ImportAuditService auditService;

    private final JapaneseTextValidator japaneseValidator = new JapaneseTextValidator();
    private final KanaReadingValidator kanaValidator = new KanaReadingValidator();
    private final VietnameseTextValidator vietnameseValidator = new VietnameseTextValidator();

    public GrammarExcelService(GrammarTopicRepository topicRepository,
                               GrammarExampleRepository exampleRepository,
                               GrammarQuizQuestionRepository questionRepository,
                               GrammarQuizChoiceRepository choiceRepository,
                               ExcelUploadValidator uploadValidator,
                               ImportSessionStorage sessionStorage,
                               ImportAuditService auditService) {
        this.topicRepository = topicRepository;
        this.exampleRepository = exampleRepository;
        this.questionRepository = questionRepository;
        this.choiceRepository = choiceRepository;
        this.uploadValidator = uploadValidator;
        this.sessionStorage = sessionStorage;
        this.auditService = auditService;
    }

    // =========================================================================
    // 1. Xem trước tệp Excel tải lên (Preview)
    // =========================================================================

    public GrammarImportPreviewReport preview(MultipartFile file, String clientIp) throws IOException {
        uploadValidator.validate(file);

        byte[] bytes = file.getBytes();
        String fileHash = DigestUtils.sha256Hex(bytes);

        String token = sessionStorage.storeFile(clientIp, file);

        DataFormatter formatter = new DataFormatter();
        GrammarImportPreviewReport report = GrammarImportPreviewReport.builder()
                .token(token)
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();

        try (InputStream is = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet grammarSheet = workbook.getSheet("Grammar");
            if (grammarSheet == null) {
                report.getErrors().add("Tệp Excel thiếu trang tính bắt buộc: 'Grammar'.");
                return report;
            }

            Sheet quizSheet = workbook.getSheet("Quiz");
            if (quizSheet == null) {
                report.getErrors().add("Tệp Excel thiếu trang tính bắt buộc: 'Quiz'.");
                return report;
            }

            // 1. Đọc sheet "Grammar"
            Map<String, GrammarImportPreviewReport.GrammarTopicPreviewDto> topicMap = new LinkedHashMap<>();
            Set<String> seenCodesInFile = new HashSet<>();
            GrammarImportPreviewReport.GrammarTopicPreviewDto currentTopic = null;

            int lastGrammarRow = grammarSheet.getLastRowNum();
            for (int r = 1; r <= lastGrammarRow; r++) {
                Row row = grammarSheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter, 9)) {
                    continue;
                }
                int displayRow = r + 1;
                checkFormulas(row, formatter, "Grammar", displayRow, report.getErrors(), 9);

                String cellCode = ExcelCellUtils.normalizeCell(formatter, row.getCell(0));
                String cellTitle = ExcelCellUtils.normalizeCell(formatter, row.getCell(1));
                String cellCat = ExcelCellUtils.normalizeCell(formatter, row.getCell(2));
                String cellFormula = ExcelCellUtils.normalizeCell(formatter, row.getCell(3));
                String cellExpl = ExcelCellUtils.normalizeCell(formatter, row.getCell(4));
                String cellNotes = ExcelCellUtils.normalizeCell(formatter, row.getCell(5));
                String cellJp = ExcelCellUtils.normalizeCell(formatter, row.getCell(6));
                String cellRead = ExcelCellUtils.normalizeCell(formatter, row.getCell(7));
                String cellVi = ExcelCellUtils.normalizeCell(formatter, row.getCell(8));

                if (!cellCode.isBlank()) {
                    // Chủ đề mới
                    String cleanCode = cellCode.toUpperCase();
                    if (seenCodesInFile.contains(cleanCode)) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột A (Mã): Mã '%s' bị trùng lặp trong tệp.", displayRow, cleanCode));
                    } else if (topicRepository.existsByCode(cleanCode)) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột A (Mã): Mã '%s' đã tồn tại trong hệ thống.", displayRow, cleanCode));
                    }
                    seenCodesInFile.add(cleanCode);

                    if (cellTitle.isBlank()) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột B (Tiêu đề): Không được để trống.", displayRow));
                    } else if (cellTitle.length() > 255) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột B (Tiêu đề): Vượt quá 255 ký tự.", displayRow));
                    }

                    if (!GrammarCategory.isValid(cellCat)) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột C (Loại): Phải là 'GRAMMAR' hoặc 'SUPPLEMENT'.", displayRow));
                    }

                    if (cellFormula.length() > 500) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột D (Công thức): Vượt quá 500 ký tự.", displayRow));
                    }

                    if (cellExpl.length() > 5000) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột E (Giải thích): Vượt quá 5000 ký tự.", displayRow));
                    }
                    if (cellExpl.contains("<") || cellExpl.contains(">")) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột E (Giải thích): Không được chứa mã HTML.", displayRow));
                    }

                    if (cellNotes.length() > 1000) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d, Cột F (Ghi chú): Vượt quá 1000 ký tự.", displayRow));
                    }

                    currentTopic = GrammarImportPreviewReport.GrammarTopicPreviewDto.builder()
                            .rowNum(displayRow)
                            .code(cleanCode)
                            .title(cellTitle)
                            .category(GrammarCategory.isValid(cellCat) ? GrammarCategory.fromString(cellCat).name() : cellCat)
                            .formula(cellFormula)
                            .explanation(cellExpl)
                            .notes(cellNotes)
                            .examples(new ArrayList<>())
                            .build();

                    topicMap.put(cleanCode, currentTopic);
                    report.getTopics().add(currentTopic);

                    // Kiểm tra ví dụ ở cùng hàng nếu có
                    if (!cellJp.isBlank() || !cellVi.isBlank()) {
                        validateAndAddExample(currentTopic, displayRow, cellJp, cellRead, cellVi, report.getErrors());
                    }
                } else if (!cellJp.isBlank() || !cellVi.isBlank()) {
                    // Ví dụ tiếp theo của chủ đề hàng trên
                    if (currentTopic == null) {
                        report.getErrors().add(String.format("Sheet 'Grammar', Hàng %d: Ví dụ không thuộc chủ đề nào (cột Mã trống nhưng chưa có chủ đề phía trên).", displayRow));
                    } else {
                        validateAndAddExample(currentTopic, displayRow, cellJp, cellRead, cellVi, report.getErrors());
                    }
                }
            }

            // 2. Đọc sheet "Quiz"
            int lastQuizRow = quizSheet.getLastRowNum();
            Map<String, Integer> topicQuestionCount = new HashMap<>();

            for (int r = 1; r <= lastQuizRow; r++) {
                Row row = quizSheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter, 8)) {
                    continue;
                }
                int displayRow = r + 1;
                checkFormulas(row, formatter, "Quiz", displayRow, report.getErrors(), 8);

                String code = ExcelCellUtils.normalizeCell(formatter, row.getCell(0)).toUpperCase();
                String prompt = ExcelCellUtils.normalizeCell(formatter, row.getCell(1));
                String c1 = ExcelCellUtils.normalizeCell(formatter, row.getCell(2));
                String c2 = ExcelCellUtils.normalizeCell(formatter, row.getCell(3));
                String c3 = ExcelCellUtils.normalizeCell(formatter, row.getCell(4));
                String c4 = ExcelCellUtils.normalizeCell(formatter, row.getCell(5));
                String correctStr = ExcelCellUtils.normalizeCell(formatter, row.getCell(6));
                String explanation = ExcelCellUtils.normalizeCell(formatter, row.getCell(7));

                if (code.isBlank() && prompt.isBlank()) {
                    continue;
                }

                if (code.isBlank()) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột A (Mã ngữ pháp): Không được để trống.", displayRow));
                } else if (!topicMap.containsKey(code) && !topicRepository.existsByCode(code)) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột A (Mã ngữ pháp): Không tìm thấy chủ đề '%s' trong tệp hoặc hệ thống.", displayRow, code));
                }

                if (prompt.isBlank()) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột B (Đề bài): Không được để trống.", displayRow));
                } else if (prompt.length() > 500) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột B (Đề bài): Vượt quá 500 ký tự.", displayRow));
                }

                List<String> choices = new ArrayList<>();
                if (!c1.isBlank()) choices.add(c1);
                if (!c2.isBlank()) choices.add(c2);
                if (!c3.isBlank()) choices.add(c3);
                if (!c4.isBlank()) choices.add(c4);

                if (choices.size() < 2) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d: Câu hỏi phải có ít nhất 2 phương án trả lời.", displayRow));
                }

                int correctIdx = -1;
                try {
                    correctIdx = Integer.parseInt(correctStr.trim());
                    if (correctIdx < 1 || correctIdx > choices.size()) {
                        report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột G (Đáp án đúng): Giá trị '%s' không hợp lệ. Phải là số từ 1 đến %d.", displayRow, correctStr, choices.size()));
                    }
                } catch (Exception e) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột G (Đáp án đúng): '%s' không phải số nguyên hợp lệ.", displayRow, correctStr));
                }

                if (explanation.length() > 1000) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d, Cột H (Giải thích): Vượt quá 1000 ký tự.", displayRow));
                }

                int qCount = topicQuestionCount.getOrDefault(code, 0) + 1;
                topicQuestionCount.put(code, qCount);
                if (qCount > GrammarTopic.MAX_QUESTIONS_PER_TOPIC) {
                    report.getErrors().add(String.format("Sheet 'Quiz', Hàng %d: Chủ đề '%s' vượt quá giới hạn %d câu hỏi.", displayRow, code, GrammarTopic.MAX_QUESTIONS_PER_TOPIC));
                }

                GrammarImportPreviewReport.GrammarQuizPreviewDto quizDto = GrammarImportPreviewReport.GrammarQuizPreviewDto.builder()
                        .rowNum(displayRow)
                        .grammarCode(code)
                        .prompt(prompt)
                        .choices(choices)
                        .correctIndex(correctIdx)
                        .explanation(explanation)
                        .build();

                report.getQuizQuestions().add(quizDto);
            }

            int totalEx = report.getTopics().stream().mapToInt(t -> t.getExamples().size()).sum();
            report.setTotalTopics(report.getTopics().size());
            report.setTotalExamples(totalEx);
            report.setTotalQuestions(report.getQuizQuestions().size());

            if (report.getTotalTopics() == 0) {
                report.getErrors().add("Không tìm thấy chủ đề ngữ pháp nào trong tệp Excel.");
            }
        }

        return report;
    }

    private void validateAndAddExample(GrammarImportPreviewReport.GrammarTopicPreviewDto topic,
                                       int displayRow,
                                       String jp,
                                       String reading,
                                       String meaning,
                                       List<String> errors) {
        if (topic.getExamples().size() >= GrammarTopic.MAX_EXAMPLES_PER_TOPIC) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d: Chủ đề '%s' vượt quá giới hạn %d ví dụ.", displayRow, topic.getCode(), GrammarTopic.MAX_EXAMPLES_PER_TOPIC));
            return;
        }

        if (jp.isBlank()) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột G (Ví dụ tiếng Nhật): Không được để trống.", displayRow));
        } else if (jp.length() > 500) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột G (Ví dụ tiếng Nhật): Vượt quá 500 ký tự.", displayRow));
        } else if (!japaneseValidator.isValid(jp, null)) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột G (Ví dụ tiếng Nhật): Phải chứa ít nhất một ký tự tiếng Nhật hợp lệ.", displayRow));
        }

        if (reading.length() > 500) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột H (Cách đọc): Vượt quá 500 ký tự.", displayRow));
        } else if (!reading.isBlank() && !kanaValidator.isValid(reading, null)) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột H (Cách đọc): Chỉ được chứa ký tự Kana hợp lệ.", displayRow));
        }

        if (meaning.isBlank()) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột I (Nghĩa tiếng Việt): Không được để trống.", displayRow));
        } else if (meaning.length() > 500) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột I (Nghĩa tiếng Việt): Vượt quá 500 ký tự.", displayRow));
        } else if (!vietnameseValidator.isValid(meaning, null)) {
            errors.add(String.format("Sheet 'Grammar', Hàng %d, Cột I (Nghĩa tiếng Việt): Chứa ký tự không hợp lệ hoặc chứa mã HTML.", displayRow));
        }

        topic.getExamples().add(GrammarImportPreviewReport.GrammarExamplePreviewDto.builder()
                .rowNum(displayRow)
                .japanese(jp)
                .reading(reading)
                .meaningVi(meaning)
                .build());
    }

    private void checkFormulas(Row row, DataFormatter formatter, String sheetName, int rowNum, List<String> errors, int maxCols) {
        for (int c = 0; c < maxCols; c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                if (ExcelCellUtils.isFormula(cell)) {
                    errors.add(String.format("Sheet '%s', Hàng %d, Cột %c: Phát hiện công thức Excel bị cấm.", sheetName, rowNum, (char) ('A' + c)));
                } else {
                    String raw = formatter.formatCellValue(cell);
                    if (raw != null && !raw.isBlank()) {
                        String trimmed = raw.trim();
                        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("@")) {
                            errors.add(String.format("Sheet '%s', Hàng %d, Cột %c: Phát hiện ký tự khởi đầu công thức bị cấm ('%c').", sheetName, rowNum, (char) ('A' + c), trimmed.charAt(0)));
                        } else if (trimmed.startsWith("-")) {
                            // Cột E (Giải thích) cho phép gạch đầu dòng "- "
                            if (!("Grammar".equals(sheetName) && c == 4 && trimmed.startsWith("- "))) {
                                errors.add(String.format("Sheet '%s', Hàng %d, Cột %c: Phát hiện ký tự khởi đầu công thức bị cấm ('-').", sheetName, rowNum, (char) ('A' + c)));
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter, int maxCols) {
        for (int c = 0; c < maxCols; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !ExcelCellUtils.normalizeCell(formatter, cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // 2. Xác nhận và nhập vào CSDL (Commit với toàn phần transaction)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public int commit(String token, String clientIp) throws IOException {
        ImportSessionStorage.StoredFile stored = sessionStorage.retrieveAndConsume(token, clientIp)
                .orElseThrow(() -> new IllegalArgumentException("Phiên tải lên không hợp lệ hoặc đã hết hạn (quá 10 phút). Vui lòng tải lại tệp."));

        byte[] bytes = stored.getContent();
        String fileHash = DigestUtils.sha256Hex(bytes);

        DataFormatter formatter = new DataFormatter();
        int totalSaved = 0;

        try (InputStream is = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet grammarSheet = workbook.getSheet("Grammar");
            Sheet quizSheet = workbook.getSheet("Quiz");

            if (grammarSheet == null || quizSheet == null) {
                throw new IllegalArgumentException("Tệp Excel thiếu trang tính bắt buộc.");
            }

            Integer maxSort = topicRepository.findMaxSortOrder();
            int nextSort = (maxSort != null) ? maxSort + 1 : 1;

            Map<String, GrammarTopic> savedTopics = new HashMap<>();
            GrammarTopic currentTopic = null;
            int exOrder = 1;

            int lastGrammarRow = grammarSheet.getLastRowNum();
            for (int r = 1; r <= lastGrammarRow; r++) {
                Row row = grammarSheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter, 9)) {
                    continue;
                }

                String cellCode = ExcelCellUtils.normalizeCell(formatter, row.getCell(0));
                String cellTitle = ExcelCellUtils.normalizeCell(formatter, row.getCell(1));
                String cellCat = ExcelCellUtils.normalizeCell(formatter, row.getCell(2));
                String cellFormula = ExcelCellUtils.normalizeCell(formatter, row.getCell(3));
                String cellExpl = ExcelCellUtils.normalizeCell(formatter, row.getCell(4));
                String cellNotes = ExcelCellUtils.normalizeCell(formatter, row.getCell(5));
                String cellJp = ExcelCellUtils.normalizeCell(formatter, row.getCell(6));
                String cellRead = ExcelCellUtils.normalizeCell(formatter, row.getCell(7));
                String cellVi = ExcelCellUtils.normalizeCell(formatter, row.getCell(8));

                if (!cellCode.isBlank()) {
                    String cleanCode = cellCode.toUpperCase();
                    if (topicRepository.existsByCode(cleanCode)) {
                        throw new IllegalArgumentException("Mã chủ đề ngữ pháp '" + cleanCode + "' đã tồn tại trong hệ thống.");
                    }

                    GrammarCategory cat = GrammarCategory.fromString(cellCat);
                    currentTopic = GrammarTopic.builder()
                            .code(cleanCode)
                            .title(TextNormalizer.normalize(cellTitle))
                            .category(cat)
                            .formula(cellFormula.isBlank() ? null : cellFormula)
                            .explanation(cellExpl.isBlank() ? null : cellExpl)
                            .notes(cellNotes.isBlank() ? null : cellNotes)
                            .sortOrder(nextSort++)
                            .build();

                    currentTopic = topicRepository.save(currentTopic);
                    savedTopics.put(cleanCode, currentTopic);
                    totalSaved++;
                    exOrder = 1;

                    if (!cellJp.isBlank()) {
                        GrammarExample ex = GrammarExample.builder()
                                .grammarTopic(currentTopic)
                                .sortOrder(exOrder++)
                                .japanese(TextNormalizer.normalize(cellJp))
                                .reading(cellRead.isBlank() ? null : TextNormalizer.normalize(cellRead))
                                .meaningVi(TextNormalizer.normalize(cellVi))
                                .build();
                        currentTopic.addExample(ex);
                        exampleRepository.save(ex);
                    }
                } else if (!cellJp.isBlank() && currentTopic != null) {
                    GrammarExample ex = GrammarExample.builder()
                            .grammarTopic(currentTopic)
                            .sortOrder(exOrder++)
                            .japanese(TextNormalizer.normalize(cellJp))
                            .reading(cellRead.isBlank() ? null : TextNormalizer.normalize(cellRead))
                            .meaningVi(TextNormalizer.normalize(cellVi))
                            .build();
                    currentTopic.addExample(ex);
                    exampleRepository.save(ex);
                }
            }

            // Đọc và lưu Sheet Quiz
            int lastQuizRow = quizSheet.getLastRowNum();
            Map<String, Integer> qOrders = new HashMap<>();

            for (int r = 1; r <= lastQuizRow; r++) {
                Row row = quizSheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter, 8)) {
                    continue;
                }

                String code = ExcelCellUtils.normalizeCell(formatter, row.getCell(0)).toUpperCase();
                String prompt = ExcelCellUtils.normalizeCell(formatter, row.getCell(1));
                String c1 = ExcelCellUtils.normalizeCell(formatter, row.getCell(2));
                String c2 = ExcelCellUtils.normalizeCell(formatter, row.getCell(3));
                String c3 = ExcelCellUtils.normalizeCell(formatter, row.getCell(4));
                String c4 = ExcelCellUtils.normalizeCell(formatter, row.getCell(5));
                String correctStr = ExcelCellUtils.normalizeCell(formatter, row.getCell(6));
                String expl = ExcelCellUtils.normalizeCell(formatter, row.getCell(7));

                GrammarTopic topic = savedTopics.get(code);
                if (topic == null) {
                    topic = topicRepository.findByCode(code).orElse(null);
                }
                if (topic == null) {
                    continue;
                }

                int qSort = qOrders.getOrDefault(code, 1);
                qOrders.put(code, qSort + 1);

                GrammarQuizQuestion q = GrammarQuizQuestion.builder()
                        .grammarTopic(topic)
                        .sortOrder(qSort)
                        .prompt(prompt)
                        .explanation(expl.isBlank() ? null : expl)
                        .build();

                List<String> choiceTexts = new ArrayList<>();
                if (!c1.isBlank()) choiceTexts.add(c1);
                if (!c2.isBlank()) choiceTexts.add(c2);
                if (!c3.isBlank()) choiceTexts.add(c3);
                if (!c4.isBlank()) choiceTexts.add(c4);

                int correctIdx = 1;
                try {
                    correctIdx = Integer.parseInt(correctStr.trim());
                } catch (Exception ignored) {
                }

                int chOrder = 1;
                for (int i = 0; i < choiceTexts.size(); i++) {
                    GrammarQuizChoice choice = GrammarQuizChoice.builder()
                            .sortOrder(chOrder++)
                            .text(choiceTexts.get(i))
                            .correct((i + 1) == correctIdx)
                            .build();
                    q.addChoice(choice);
                }

                topic.addQuestion(q);
                questionRepository.save(q);
            }

            // Ghi audit log
            auditService.log(clientIp, stored.getOriginalFilename(), fileHash,
                    totalSaved, 0, 0, "SUCCESS", "Đã nhập thành công " + totalSaved + " chủ đề ngữ pháp từ Excel.");

            return totalSaved;
        } catch (Exception ex) {
            auditService.log(clientIp, stored.getOriginalFilename(), fileHash,
                    0, 0, 0, "FAILED", "Lỗi nhập Excel: " + ex.getMessage());
            throw ex;
        }
    }

    // =========================================================================
    // 3. Xuất dữ liệu ra Excel (Export)
    // =========================================================================

    public byte[] exportGrammar(GrammarCategory category) throws IOException {
        List<GrammarTopic> topics;
        if (category != null) {
            topics = topicRepository.findByCategoryOrderBySortOrderAsc(category);
        } else {
            topics = topicRepository.findAll();
        }

        return buildWorkbook(topics);
    }

    // =========================================================================
    // 4. Tạo tệp mẫu (Template) với dữ liệu mới và metadata "Project Nihon Team"
    // =========================================================================

    public byte[] generateTemplate() throws IOException {
        List<GrammarTopic> sampleTopics = createSampleData();
        return buildWorkbook(sampleTopics);
    }

    private List<GrammarTopic> createSampleData() {
        List<GrammarTopic> list = new ArrayList<>();

        // Chủ đề 1: 〜から (Chỉ nguyên nhân, lý do)
        GrammarTopic t1 = GrammarTopic.builder()
                .code("N5-GR-01")
                .title("Cấu trúc 〜から (Vì, bởi vì)")
                .category(GrammarCategory.GRAMMAR)
                .formula("[Thể thường / Thể lịch sự] + から")
                .explanation("Dùng để biểu thị nguyên nhân, lý do của hành động hoặc sự việc.\n- Thường dùng trong văn nói hàng ngày\n- Mệnh đề sau có thể dùng câu mệnh lệnh, rủ rê hoặc ý chí")
                .notes("Trang trọng hơn có thể dùng 〜ので trong ngữ cảnh công sở.")
                .sortOrder(1)
                .examples(new ArrayList<>())
                .questions(new ArrayList<>())
                .build();

        t1.addExample(GrammarExample.builder()
                .sortOrder(1)
                .japanese("雨が降っているから、傘を持っていきます。")
                .reading("あめがふっているから、かさをもっていきます。")
                .meaningVi("Vì trời đang mưa nên tôi mang theo ô.")
                .build());

        t1.addExample(GrammarExample.builder()
                .sortOrder(2)
                .japanese("時間がありませんから、タクシーで行きましょう。")
                .reading("じかんがありませんから、タクシーでいきましょう。")
                .meaningVi("Vì không có thời gian nên chúng ta hãy đi bằng taxi.")
                .build());

        GrammarQuizQuestion q1 = GrammarQuizQuestion.builder()
                .sortOrder(1)
                .prompt("あしたは　テストが　ある（　）、きょうは　べんきょうします。")
                .explanation("Điền から để chỉ lý do cho hành động học bài hôm nay.")
                .choices(new ArrayList<>())
                .build();

        q1.addChoice(GrammarQuizChoice.builder().sortOrder(1).text("から").correct(true).build());
        q1.addChoice(GrammarQuizChoice.builder().sortOrder(2).text("けど").correct(false).build());
        q1.addChoice(GrammarQuizChoice.builder().sortOrder(3).text("より").correct(false).build());
        q1.addChoice(GrammarQuizChoice.builder().sortOrder(4).text("ほど").correct(false).build());
        t1.addQuestion(q1);

        // Chủ đề 2: 〜てもいいです (Xin phép, cho phép)
        GrammarTopic t2 = GrammarTopic.builder()
                .code("N5-GR-02")
                .title("Cấu trúc 〜てもいいです (Có thể, được phép làm gì)")
                .category(GrammarCategory.GRAMMAR)
                .formula("Động từ thể Te + もいいです")
                .explanation("Dùng để xin phép thực hiện hành động hoặc cho phép đối phương làm gì.\n- Thể nghi vấn 〜てもいいですか dùng khi xin phép lịch sự\n- Trả lời đồng ý: ええ、いいですよ / どうぞ")
                .notes("Cấm đoán không được làm gì thì dùng 〜てはいけません.")
                .sortOrder(2)
                .examples(new ArrayList<>())
                .questions(new ArrayList<>())
                .build();

        t2.addExample(GrammarExample.builder()
                .sortOrder(1)
                .japanese("ここで写真を撮ってもいいですか。")
                .reading("ここでしゃしんをとってもいいですか。")
                .meaningVi("Tôi chụp ảnh ở đây có được không?")
                .build());

        t2.addExample(GrammarExample.builder()
                .sortOrder(2)
                .japanese("この本を読んでもいいです。")
                .reading("このほんをよんでもいいです。")
                .meaningVi("Bạn có thể đọc cuốn sách này.")
                .build());

        GrammarQuizQuestion q2 = GrammarQuizQuestion.builder()
                .sortOrder(1)
                .prompt("ここで　たばこを　（　）もいいですか。")
                .explanation("Động từ 吸う (hút) chia thể Te là 吸って.")
                .choices(new ArrayList<>())
                .build();

        q2.addChoice(GrammarQuizChoice.builder().sortOrder(1).text("すって").correct(true).build());
        q2.addChoice(GrammarQuizChoice.builder().sortOrder(2).text("すい").correct(false).build());
        q2.addChoice(GrammarQuizChoice.builder().sortOrder(3).text("すう").correct(false).build());
        q2.addChoice(GrammarQuizChoice.builder().sortOrder(4).text("すった").correct(false).build());
        t2.addQuestion(q2);

        list.add(t1);
        list.add(t2);
        return list;
    }

    private byte[] buildWorkbook(List<GrammarTopic> topics) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Thiết lập metadata "Project Nihon Team"
            if (workbook instanceof XSSFWorkbook xssf) {
                POIXMLProperties props = xssf.getProperties();
                POIXMLProperties.CoreProperties core = props.getCoreProperties();
                core.setCreator("Project Nihon Team");
                core.setDescription("Project Nihon Grammar & Supplement Export Template");
            }

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // ==========================================
            // Sheet 1: Grammar
            // ==========================================
            Sheet gSheet = workbook.createSheet("Grammar");
            Row gHeader = gSheet.createRow(0);
            String[] gHeaders = {
                    "Code", "Tiêu đề", "Loại", "Công thức", "Giải thích",
                    "Ghi chú", "Ví dụ tiếng Nhật", "Cách đọc", "Nghĩa tiếng Việt"
            };
            for (int i = 0; i < gHeaders.length; i++) {
                Cell cell = gHeader.createCell(i);
                cell.setCellValue(gHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int gRowNum = 1;
            for (GrammarTopic topic : topics) {
                List<GrammarExample> examples = topic.getExamples();
                if (examples == null || examples.isEmpty()) {
                    Row row = gSheet.createRow(gRowNum++);
                    createCell(row, 0, ExcelCellUtils.sanitizeForExport(topic.getCode()), dataStyle);
                    createCell(row, 1, ExcelCellUtils.sanitizeForExport(topic.getTitle()), dataStyle);
                    createCell(row, 2, ExcelCellUtils.sanitizeForExport(topic.getCategory().name()), dataStyle);
                    createCell(row, 3, ExcelCellUtils.sanitizeForExport(topic.getFormula()), dataStyle);
                    createCell(row, 4, ExcelCellUtils.sanitizeForExport(topic.getExplanation()), dataStyle);
                    createCell(row, 5, ExcelCellUtils.sanitizeForExport(topic.getNotes()), dataStyle);
                    createCell(row, 6, "", dataStyle);
                    createCell(row, 7, "", dataStyle);
                    createCell(row, 8, "", dataStyle);
                } else {
                    for (int i = 0; i < examples.size(); i++) {
                        GrammarExample ex = examples.get(i);
                        Row row = gSheet.createRow(gRowNum++);
                        if (i == 0) {
                            createCell(row, 0, ExcelCellUtils.sanitizeForExport(topic.getCode()), dataStyle);
                            createCell(row, 1, ExcelCellUtils.sanitizeForExport(topic.getTitle()), dataStyle);
                            createCell(row, 2, ExcelCellUtils.sanitizeForExport(topic.getCategory().name()), dataStyle);
                            createCell(row, 3, ExcelCellUtils.sanitizeForExport(topic.getFormula()), dataStyle);
                            createCell(row, 4, ExcelCellUtils.sanitizeForExport(topic.getExplanation()), dataStyle);
                            createCell(row, 5, ExcelCellUtils.sanitizeForExport(topic.getNotes()), dataStyle);
                        } else {
                            createCell(row, 0, "", dataStyle);
                            createCell(row, 1, "", dataStyle);
                            createCell(row, 2, "", dataStyle);
                            createCell(row, 3, "", dataStyle);
                            createCell(row, 4, "", dataStyle);
                            createCell(row, 5, "", dataStyle);
                        }
                        createCell(row, 6, ExcelCellUtils.sanitizeForExport(ex.getJapanese()), dataStyle);
                        createCell(row, 7, ExcelCellUtils.sanitizeForExport(ex.getReading()), dataStyle);
                        createCell(row, 8, ExcelCellUtils.sanitizeForExport(ex.getMeaningVi()), dataStyle);
                    }
                }
            }

            for (int i = 0; i < gHeaders.length; i++) {
                gSheet.autoSizeColumn(i);
            }

            // ==========================================
            // Sheet 2: Quiz
            // ==========================================
            Sheet qSheet = workbook.createSheet("Quiz");
            Row qHeader = qSheet.createRow(0);
            String[] qHeaders = {
                    "Mã ngữ pháp", "Đề bài", "Đáp án 1", "Đáp án 2",
                    "Đáp án 3", "Đáp án 4", "Đáp án đúng", "Giải thích"
            };
            for (int i = 0; i < qHeaders.length; i++) {
                Cell cell = qHeader.createCell(i);
                cell.setCellValue(qHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int qRowNum = 1;
            for (GrammarTopic topic : topics) {
                if (topic.getQuestions() != null) {
                    for (GrammarQuizQuestion q : topic.getQuestions()) {
                        Row row = qSheet.createRow(qRowNum++);
                        createCell(row, 0, ExcelCellUtils.sanitizeForExport(topic.getCode()), dataStyle);
                        createCell(row, 1, ExcelCellUtils.sanitizeForExport(q.getPrompt()), dataStyle);

                        List<GrammarQuizChoice> choices = q.getChoices() != null ? q.getChoices() : Collections.emptyList();
                        int correctIdx = 1;
                        for (int i = 0; i < 4; i++) {
                            if (i < choices.size()) {
                                GrammarQuizChoice c = choices.get(i);
                                createCell(row, 2 + i, ExcelCellUtils.sanitizeForExport(c.getText()), dataStyle);
                                if (c.isCorrect()) {
                                    correctIdx = i + 1;
                                }
                            } else {
                                createCell(row, 2 + i, "", dataStyle);
                            }
                        }

                        createCell(row, 6, String.valueOf(correctIdx), dataStyle);
                        createCell(row, 7, ExcelCellUtils.sanitizeForExport(q.getExplanation()), dataStyle);
                    }
                }
            }

            for (int i = 0; i < qHeaders.length; i++) {
                qSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
}

