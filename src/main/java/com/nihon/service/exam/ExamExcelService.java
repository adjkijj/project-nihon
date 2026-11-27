package com.nihon.service.exam;

import com.nihon.dto.exam.ExamExcelRow;
import com.nihon.dto.exam.ExamImportPreviewReport;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamAnswerKeyRepository;
import com.nihon.repository.ExamChoiceRepository;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.repository.ExamQuestionRepository;
import com.nihon.repository.ExamSectionRepository;
import com.nihon.service.excel.ExcelCellUtils;
import com.nihon.service.excel.ExcelUploadValidator;
import com.nihon.service.excel.ImportAuditService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.validation.JapaneseTextValidator;
import com.nihon.validation.TextNormalizer;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ooxml.POIXMLProperties;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Service xử lý Nhập (Import hai bước: xem trước & xác nhận) và Xuất (Export) Đề thi bằng tệp Excel .xlsx (F41).
 * Tuân thủ bảo mật chống Formula Injection, kiểm soát IP, giới hạn kích thước và audit log.
 */
@Service
public class ExamExcelService {

    private static final Logger log = LoggerFactory.getLogger(ExamExcelService.class);

    private final ExamPaperRepository paperRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamChoiceRepository choiceRepository;
    private final ExamAnswerKeyRepository answerKeyRepository;
    private final ExcelUploadValidator uploadValidator;
    private final ImportSessionStorage sessionStorage;
    private final ImportAuditService auditService;

    public ExamExcelService(ExamPaperRepository paperRepository,
                            ExamSectionRepository sectionRepository,
                            ExamQuestionRepository questionRepository,
                            ExamChoiceRepository choiceRepository,
                            ExamAnswerKeyRepository answerKeyRepository,
                            ExcelUploadValidator uploadValidator,
                            ImportSessionStorage sessionStorage,
                            ImportAuditService auditService) {
        this.paperRepository = paperRepository;
        this.sectionRepository = sectionRepository;
        this.questionRepository = questionRepository;
        this.choiceRepository = choiceRepository;
        this.answerKeyRepository = answerKeyRepository;
        this.uploadValidator = uploadValidator;
        this.sessionStorage = sessionStorage;
        this.auditService = auditService;
    }

    // =========================================================================
    // 1. Xem trước tệp Excel tải lên (Preview)
    // =========================================================================

    public ExamImportPreviewReport preview(MultipartFile file, String clientIp) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn tệp tin Excel (.xlsx) để tải lên.");
        }

        // 1. Thẩm định an toàn tệp tin (kích thước, magic bytes, zip bomb, chặn macro)
        uploadValidator.validate(file);

        // 2. Lưu tệp tạm vào session storage để có token 10 phút
        String token = sessionStorage.storeFile(clientIp, file);
        byte[] bytes = file.getBytes();

        // 3. Phân tích nội dung tệp Excel
        return parseAndValidateWorkbook(bytes, token, file.getOriginalFilename());
    }

    public ExamImportPreviewReport parseAndValidateWorkbook(byte[] bytes, String token, String filename) {
        ExamImportPreviewReport report = ExamImportPreviewReport.builder()
                .token(token)
                .fileName(filename)
                .rows(new ArrayList<>())
                .globalErrors(new ArrayList<>())
                .build();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = ExcelCellUtils.findSheetCaseInsensitive(workbook, "Exam");
            if (sheet == null) {
                if (workbook.getNumberOfSheets() > 0) {
                    sheet = workbook.getSheetAt(0);
                } else {
                    report.getGlobalErrors().add("Tệp tin Excel không có bất kỳ trang tính (sheet) nào.");
                    return report;
                }
            }

            DataFormatter formatter = new DataFormatter();
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                report.getGlobalErrors().add("Trang tính 'Exam' không chứa dữ liệu câu hỏi.");
                return report;
            }

            // Kế thừa các giá trị hàng trên nếu ô để trống
            String inheritedCode = null;
            String inheritedTitle = null;
            Integer inheritedDuration = null;
            String inheritedCategory = null;
            String inheritedSection = null;

            Map<String, List<ExamExcelRow>> sectionMap = new LinkedHashMap<>();

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(formatter, row)) {
                    continue;
                }

                int displayRowNum = r + 1; // Số hàng hiển thị theo Excel (1-based)
                ExamExcelRow rowData = ExamExcelRow.builder().rowNum(displayRowNum).build();

                // Kiểm tra Formula Injection & Số thẻ thanh toán
                checkFormulasAndCardNumbers(row, formatter, displayRowNum, rowData);

                // Cột A: Code đề
                String cellA = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(0));
                if (cellA != null && !cellA.isBlank()) {
                    inheritedCode = TextNormalizer.normalize(cellA.trim().toUpperCase());
                }
                rowData.setPaperCode(inheritedCode);

                // Cột B: Tên đề
                String cellB = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(1));
                if (cellB != null && !cellB.isBlank()) {
                    inheritedTitle = TextNormalizer.normalize(cellB.trim());
                }
                rowData.setPaperTitle(inheritedTitle);

                // Cột C: Thời lượng (phút)
                String cellC = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(2));
                if (cellC != null && !cellC.isBlank()) {
                    try {
                        inheritedDuration = (int) Double.parseDouble(cellC.trim());
                    } catch (NumberFormatException e) {
                        rowData.addError(String.format("Hàng %d, Cột C: Thời lượng làm bài '%s' không phải số nguyên hợp lệ.", displayRowNum, cellC));
                    }
                }
                rowData.setDurationMinutes(inheritedDuration);

                // Cột D: Loại đề
                String cellD = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(3));
                if (cellD != null && !cellD.isBlank()) {
                    inheritedCategory = TextNormalizer.normalize(cellD.trim().toUpperCase());
                }
                rowData.setCategory(inheritedCategory);

                // Cột E: Phần thi
                String cellE = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(4));
                if (cellE != null && !cellE.isBlank()) {
                    inheritedSection = TextNormalizer.normalize(cellE.trim());
                }
                rowData.setSectionTitle(inheritedSection);

                // Cột F: Loại câu hỏi
                String cellF = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(5));
                rowData.setQuestionType(cellF != null ? TextNormalizer.normalize(cellF.trim()) : null);

                // Cột G: Đề bài tiếng Nhật
                String cellG = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(6));
                rowData.setPromptJa(cellG != null ? TextNormalizer.normalize(cellG.trim()) : null);

                // Cột H: Đề bài tiếng Việt
                String cellH = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(7));
                rowData.setPromptVi(cellH != null ? TextNormalizer.normalize(cellH.trim()) : null);

                // Cột I: Điểm
                String cellI = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(8));
                if (cellI != null && !cellI.isBlank()) {
                    try {
                        rowData.setPoints(new BigDecimal(cellI.trim().replace(",", ".")));
                    } catch (NumberFormatException e) {
                        rowData.addError(String.format("Hàng %d, Cột I: Điểm số '%s' không phải số thập phân hợp lệ.", displayRowNum, cellI));
                        rowData.setPoints(BigDecimal.ONE);
                    }
                } else {
                    rowData.setPoints(BigDecimal.ONE);
                }

                // Cột J - M: Đáp án 1 - 4
                rowData.setChoice1(ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(9)));
                rowData.setChoice2(ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(10)));
                rowData.setChoice3(ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(11)));
                rowData.setChoice4(ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(12)));

                // Cột N: Đáp án đúng
                String cellN = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(13));
                rowData.setCorrectAnswer(cellN != null ? TextNormalizer.normalize(cellN.trim()) : null);

                // Cột O: Giải thích
                String cellO = ExcelCellUtils.normalizeCellOrNull(formatter, row.getCell(14));
                rowData.setExplanation(cellO != null ? TextNormalizer.normalize(cellO.trim()) : null);

                // Thẩm định nghiệp vụ chi tiết cho hàng
                validateRowData(rowData, displayRowNum);

                report.getRows().add(rowData);

                if (rowData.getSectionTitle() != null && !rowData.getSectionTitle().isBlank()) {
                    sectionMap.computeIfAbsent(rowData.getSectionTitle(), k -> new ArrayList<>()).add(rowData);
                }
            }

            if (report.getRows().isEmpty()) {
                report.getGlobalErrors().add("Không tìm thấy dòng dữ liệu câu hỏi nào trong tệp Excel.");
                return report;
            }

            // Thiết lập thông tin tóm tắt đề thi từ dòng đầu tiên
            ExamExcelRow firstRow = report.getRows().get(0);
            report.setPaperCode(firstRow.getPaperCode());
            report.setPaperTitle(firstRow.getPaperTitle());
            report.setDurationMinutes(firstRow.getDurationMinutes() != null ? firstRow.getDurationMinutes() : 60);
            report.setCategory(firstRow.getCategory());
            report.setSectionCount(sectionMap.size());
            report.setQuestionCount(report.getRows().size());

            // Thẩm định giới hạn toàn đề
            if (report.getPaperCode() == null || report.getPaperCode().isBlank()) {
                report.getGlobalErrors().add("Mã đề thi (Cột A) bị thiếu hoặc để trống ở dòng đầu tiên.");
            } else {
                if (paperRepository.existsByCode(report.getPaperCode())) {
                    report.getGlobalErrors().add(String.format(
                            "Mã đề thi '%s' đã tồn tại trong hệ thống. Vui lòng đổi mã đề khác trong tệp Excel.",
                            report.getPaperCode()
                    ));
                }
            }

            if (report.getPaperTitle() == null || report.getPaperTitle().isBlank()) {
                report.getGlobalErrors().add("Tên đề thi (Cột B) bị thiếu hoặc để trống ở dòng đầu tiên.");
            }

            if (sectionMap.size() > ExamValidator.MAX_SECTIONS_PER_EXAM) {
                report.getGlobalErrors().add(String.format(
                        "Đề thi vượt quá giới hạn tối đa %d phần thi (tệp hiện có: %d phần).",
                        ExamValidator.MAX_SECTIONS_PER_EXAM, sectionMap.size()
                ));
            }

            if (report.getRows().size() > ExamValidator.MAX_QUESTIONS_PER_EXAM) {
                report.getGlobalErrors().add(String.format(
                        "Đề thi vượt quá giới hạn tối đa %d câu hỏi (tệp hiện có: %d câu).",
                        ExamValidator.MAX_QUESTIONS_PER_EXAM, report.getRows().size()
                ));
            }

        } catch (Exception e) {
            log.error("Lỗi khi đọc tệp Excel đề thi: {}", e.getMessage(), e);
            report.getGlobalErrors().add("Không thể đọc tệp Excel: " + e.getMessage());
        }

        return report;
    }

    private void validateRowData(ExamExcelRow rowData, int rowNum) {
        if (rowData.getPaperCode() == null || rowData.getPaperCode().isBlank()) {
            rowData.addError(String.format("Hàng %d, Cột A: Mã đề thi không được để trống.", rowNum));
        } else if (rowData.getPaperCode().length() > 50) {
            rowData.addError(String.format("Hàng %d, Cột A: Mã đề thi vượt quá 50 ký tự.", rowNum));
        }

        if (rowData.getPaperTitle() == null || rowData.getPaperTitle().isBlank()) {
            rowData.addError(String.format("Hàng %d, Cột B: Tên đề thi không được để trống.", rowNum));
        } else if (rowData.getPaperTitle().length() > 255) {
            rowData.addError(String.format("Hàng %d, Cột B: Tên đề thi vượt quá 255 ký tự.", rowNum));
        }

        if (rowData.getDurationMinutes() == null || rowData.getDurationMinutes() < 1 || rowData.getDurationMinutes() > 300) {
            rowData.addError(String.format("Hàng %d, Cột C: Thời lượng làm bài phải từ 1 đến 300 phút.", rowNum));
        }

        if (!ExamCategory.isValid(rowData.getCategory())) {
            rowData.addError(String.format("Hàng %d, Cột D: Loại đề '%s' không hợp lệ (hợp lệ: MIDTERM, MOCK, QUICK_TEST, REVIEW).", rowNum, rowData.getCategory()));
        }

        if (rowData.getSectionTitle() == null || rowData.getSectionTitle().isBlank()) {
            rowData.addError(String.format("Hàng %d, Cột E: Phần thi không được để trống.", rowNum));
        } else if (rowData.getSectionTitle().length() > 255) {
            rowData.addError(String.format("Hàng %d, Cột E: Tiêu đề phần thi vượt quá 255 ký tự.", rowNum));
        }

        if (!ExamQuestionType.isValid(rowData.getQuestionType())) {
            rowData.addError(String.format("Hàng %d, Cột F: Loại câu hỏi '%s' không hợp lệ (hợp lệ: MULTIPLE_CHOICE, TYPING, ORDERING, TRANSLATION, ESSAY).", rowNum, rowData.getQuestionType()));
            return;
        }

        ExamQuestionType type = ExamQuestionType.fromString(rowData.getQuestionType());

        // Kiểm tra đề bài
        boolean hasJa = rowData.getPromptJa() != null && !rowData.getPromptJa().isBlank();
        boolean hasVi = rowData.getPromptVi() != null && !rowData.getPromptVi().isBlank();
        if (!hasJa && !hasVi) {
            rowData.addError(String.format("Hàng %d, Cột G/H: Câu hỏi phải có ít nhất đề bài tiếng Nhật hoặc tiếng Việt.", rowNum));
        }

        if (hasJa) {
            JapaneseTextValidator jValidator = new JapaneseTextValidator();
            if (!jValidator.isValid(rowData.getPromptJa(), null)) {
                rowData.addError(String.format("Hàng %d, Cột G: Đề bài tiếng Nhật '%s' phải chứa ít nhất một ký tự tiếng Nhật hợp lệ (Hiragana, Katakana hoặc Kanji).", rowNum, rowData.getPromptJa()));
            }
        }

        // Kiểm tra đáp án theo loại
        List<String> choices = new ArrayList<>();
        if (rowData.getChoice1() != null && !rowData.getChoice1().isBlank()) choices.add(rowData.getChoice1().trim());
        if (rowData.getChoice2() != null && !rowData.getChoice2().isBlank()) choices.add(rowData.getChoice2().trim());
        if (rowData.getChoice3() != null && !rowData.getChoice3().isBlank()) choices.add(rowData.getChoice3().trim());
        if (rowData.getChoice4() != null && !rowData.getChoice4().isBlank()) choices.add(rowData.getChoice4().trim());

        Integer correctChoiceIdx = null;
        List<ExamValidator.AnswerKeyItem> answerKeys = new ArrayList<>();

        switch (type) {
            case MULTIPLE_CHOICE -> {
                if (rowData.getCorrectAnswer() == null || rowData.getCorrectAnswer().isBlank()) {
                    rowData.addError(String.format("Hàng %d, Cột N: Câu hỏi trắc nghiệm phải chỉ định đáp án đúng (số 1 đến 4).", rowNum));
                } else {
                    try {
                        correctChoiceIdx = (int) Double.parseDouble(rowData.getCorrectAnswer().trim());
                    } catch (NumberFormatException e) {
                        rowData.addError(String.format("Hàng %d, Cột N: Đáp án đúng '%s' phải là số nguyên từ 1 đến %d.", rowNum, rowData.getCorrectAnswer(), choices.size()));
                    }
                }
            }
            case TYPING -> {
                if (rowData.getCorrectAnswer() == null || rowData.getCorrectAnswer().isBlank()) {
                    rowData.addError(String.format("Hàng %d, Cột N: Câu hỏi gõ từ phải có ít nhất một đáp án chấp nhận.", rowNum));
                } else {
                    String[] parts = rowData.getCorrectAnswer().split("[|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) answerKeys.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, p.trim()));
                    }
                }
            }
            case ORDERING -> {
                if (rowData.getCorrectAnswer() == null || rowData.getCorrectAnswer().isBlank()) {
                    rowData.addError(String.format("Hàng %d, Cột N: Câu hỏi ghép câu phải có thứ tự đúng (ví dụ: '1,3,2,4').", rowNum));
                } else {
                    answerKeys.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, "ORDER:" + rowData.getCorrectAnswer().trim()));
                }
                // Tìm vị trí ngôi sao trong cột O nếu có
                if (rowData.getExplanation() != null && rowData.getExplanation().contains("STAR:")) {
                    String[] parts = rowData.getExplanation().split("[|;\\n\\r]+");
                    for (String p : parts) {
                        String t = p.trim();
                        if (t.startsWith("STAR:")) {
                            answerKeys.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ORDER, t));
                        }
                    }
                }
            }
            case TRANSLATION -> {
                if (rowData.getCorrectAnswer() == null || rowData.getCorrectAnswer().isBlank()) {
                    rowData.addError(String.format("Hàng %d, Cột N: Câu hỏi dịch câu phải có ít nhất một đáp án chấp nhận.", rowNum));
                } else {
                    String[] parts = rowData.getCorrectAnswer().split("[|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) answerKeys.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.ACCEPTED, p.trim()));
                    }
                }
            }
            case ESSAY -> {
                if (rowData.getCorrectAnswer() != null && !rowData.getCorrectAnswer().isBlank()) {
                    answerKeys.add(new ExamValidator.AnswerKeyItem(ExamAnswerKind.SAMPLE, rowData.getCorrectAnswer().trim()));
                } else {
                    rowData.addError(String.format("Hàng %d, Cột N: Câu hỏi tự luận phải có bài viết mẫu (SAMPLE).", rowNum));
                }
            }
        }

        try {
            ExamValidator.validateQuestion(type, rowData.getPromptJa(), rowData.getPromptVi(), choices, correctChoiceIdx, answerKeys);
        } catch (IllegalArgumentException ex) {
            rowData.addError(String.format("Hàng %d: %s", rowNum, ex.getMessage()));
        }
    }

    private void checkFormulasAndCardNumbers(Row row, DataFormatter formatter, int rowNum, ExamExcelRow rowData) {
        for (int c = 0; c <= 14; c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                if (ExcelCellUtils.isFormula(cell)) {
                    rowData.addError(String.format("Hàng %d, Cột %s: Phát hiện công thức Excel bị cấm.", rowNum, (char) ('A' + c)));
                } else {
                    String rawText = formatter.formatCellValue(cell);
                    if (rawText != null && !rawText.isBlank()) {
                        String trimmed = rawText.trim();
                        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
                            rowData.addError(String.format("Hàng %d, Cột %s: Phát hiện ký tự khởi đầu công thức bị cấm ('%s').", rowNum, (char) ('A' + c), trimmed.charAt(0)));
                        }
                        if (ExcelCellUtils.containsPotentialPaymentCard(rawText)) {
                            rowData.addError(String.format("Hàng %d, Cột %s: Phát hiện chuỗi số thẻ thanh toán nhạy cảm bị cấm.", rowNum, (char) ('A' + c)));
                        }
                    }
                }
            }
        }
    }

    private boolean isRowEmpty(DataFormatter formatter, Row row) {
        for (int c = 0; c <= 14; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !ExcelCellUtils.normalizeCell(formatter, cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // 2. Xác nhận nhập tệp Excel vào cơ sở dữ liệu (Commit)
    // =========================================================================

    @Transactional
    public ExamPaper commit(String token, String clientIp) {
        ImportSessionStorage.StoredFile storedFile = sessionStorage.retrieveAndConsume(token, clientIp)
                .orElseThrow(() -> new IllegalStateException("Phiên tải lên đã hết hạn hoặc mã xác nhận không hợp lệ. Vui lòng tải lại tệp tin."));

        byte[] bytes = storedFile.getContent();
        ExamImportPreviewReport report = parseAndValidateWorkbook(bytes, token, storedFile.getOriginalFilename());

        if (!report.canCommit()) {
            throw new IllegalStateException("Tệp tin chứa " + report.getTotalErrors() + " lỗi. Không thể nhập dữ liệu vào hệ thống.");
        }

        if (paperRepository.existsByCode(report.getPaperCode())) {
            throw new IllegalArgumentException("Mã đề thi '" + report.getPaperCode() + "' đã tồn tại trong hệ thống.");
        }

        // Tạo đề thi
        ExamPaper paper = ExamPaper.builder()
                .code(report.getPaperCode())
                .title(report.getPaperTitle())
                .durationMinutes(report.getDurationMinutes())
                .category(ExamCategory.fromString(report.getCategory()))
                .published(false)
                .build();
        paper = paperRepository.save(paper);

        // Gom nhóm theo phần thi
        Map<String, List<ExamExcelRow>> sectionMap = new LinkedHashMap<>();
        for (ExamExcelRow r : report.getRows()) {
            sectionMap.computeIfAbsent(r.getSectionTitle(), k -> new ArrayList<>()).add(r);
        }

        int sectionOrder = 1;
        int totalSavedQuestions = 0;

        for (Map.Entry<String, List<ExamExcelRow>> entry : sectionMap.entrySet()) {
            ExamSection section = ExamSection.builder()
                    .paper(paper)
                    .title(entry.getKey())
                    .sortOrder(sectionOrder++)
                    .build();
            section = sectionRepository.save(section);

            int questionOrder = 1;
            for (ExamExcelRow rowData : entry.getValue()) {
                ExamQuestionType type = ExamQuestionType.fromString(rowData.getQuestionType());

                ExamQuestion question = ExamQuestion.builder()
                        .section(section)
                        .sortOrder(questionOrder++)
                        .type(type)
                        .promptJa(rowData.getPromptJa())
                        .promptVi(rowData.getPromptVi())
                        .points(rowData.getPoints() != null ? rowData.getPoints() : BigDecimal.ONE)
                        .explanation(rowData.getExplanation())
                        .build();
                question = questionRepository.save(question);
                totalSavedQuestions++;

                saveRowChoicesAndKeys(question, type, rowData);
            }
        }

        // Ghi audit log
        String fileHash = DigestUtils.sha256Hex(bytes);
        auditService.log(
                clientIp,
                "[EXAM] " + storedFile.getOriginalFilename(),
                fileHash,
                totalSavedQuestions,
                0,
                0,
                "SUCCESS",
                null
        );

        log.info("Nhập thành công đề thi '{}' (Code: {}) với {} câu hỏi từ tệp Excel.",
                paper.getTitle(), paper.getCode(), totalSavedQuestions);

        return paper;
    }

    private void saveRowChoicesAndKeys(ExamQuestion question, ExamQuestionType type, ExamExcelRow rowData) {
        List<String> choices = new ArrayList<>();
        if (rowData.getChoice1() != null && !rowData.getChoice1().isBlank()) choices.add(rowData.getChoice1().trim());
        if (rowData.getChoice2() != null && !rowData.getChoice2().isBlank()) choices.add(rowData.getChoice2().trim());
        if (rowData.getChoice3() != null && !rowData.getChoice3().isBlank()) choices.add(rowData.getChoice3().trim());
        if (rowData.getChoice4() != null && !rowData.getChoice4().isBlank()) choices.add(rowData.getChoice4().trim());

        Integer correctChoiceIdx = null;
        if (type == ExamQuestionType.MULTIPLE_CHOICE && rowData.getCorrectAnswer() != null) {
            try {
                correctChoiceIdx = (int) Double.parseDouble(rowData.getCorrectAnswer().trim());
            } catch (NumberFormatException ignored) {}
        }

        if (type == ExamQuestionType.MULTIPLE_CHOICE || type == ExamQuestionType.ORDERING) {
            for (int i = 0; i < choices.size(); i++) {
                boolean isCorrect = (type == ExamQuestionType.MULTIPLE_CHOICE) && (correctChoiceIdx != null && correctChoiceIdx == (i + 1));
                ExamChoice choice = ExamChoice.builder()
                        .question(question)
                        .sortOrder(i + 1)
                        .text(choices.get(i))
                        .correct(isCorrect)
                        .build();
                choiceRepository.save(choice);
            }
        }

        switch (type) {
            case TYPING, TRANSLATION -> {
                if (rowData.getCorrectAnswer() != null) {
                    String[] parts = rowData.getCorrectAnswer().split("[|\\n\\r]+");
                    for (String p : parts) {
                        if (!p.isBlank()) {
                            ExamAnswerKey key = ExamAnswerKey.builder()
                                    .question(question)
                                    .kind(ExamAnswerKind.ACCEPTED)
                                    .answerText(p.trim())
                                    .build();
                            answerKeyRepository.save(key);
                        }
                    }
                }
            }
            case ORDERING -> {
                if (rowData.getCorrectAnswer() != null && !rowData.getCorrectAnswer().isBlank()) {
                    ExamAnswerKey orderKey = ExamAnswerKey.builder()
                            .question(question)
                            .kind(ExamAnswerKind.ORDER)
                            .answerText("ORDER:" + rowData.getCorrectAnswer().trim())
                            .build();
                    answerKeyRepository.save(orderKey);
                }
                if (rowData.getExplanation() != null && rowData.getExplanation().contains("STAR:")) {
                    String[] parts = rowData.getExplanation().split("[|;\\n\\r]+");
                    for (String p : parts) {
                        String t = p.trim();
                        if (t.startsWith("STAR:")) {
                            ExamAnswerKey starKey = ExamAnswerKey.builder()
                                    .question(question)
                                    .kind(ExamAnswerKind.ORDER)
                                    .answerText(t)
                                    .build();
                            answerKeyRepository.save(starKey);
                        }
                    }
                }
            }
            case ESSAY -> {
                if (rowData.getCorrectAnswer() != null && !rowData.getCorrectAnswer().isBlank()) {
                    ExamAnswerKey sampleKey = ExamAnswerKey.builder()
                            .question(question)
                            .kind(ExamAnswerKind.SAMPLE)
                            .answerText(rowData.getCorrectAnswer().trim())
                            .build();
                    answerKeyRepository.save(sampleKey);
                }
            }
            case MULTIPLE_CHOICE -> {}
        }
    }

    // =========================================================================
    // 3. Xuất đề thi ra tệp Excel chuẩn (Export)
    // =========================================================================

    public byte[] exportExam(Long examId) throws IOException {
        ExamPaper paper = paperRepository.findByIdWithSections(examId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề thi với ID: " + examId));
        for (ExamSection s : paper.getSections()) {
            for (ExamQuestion q : s.getQuestions()) {
                q.getChoices().size();
                q.getAnswerKeys().size();
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            setWorkbookMetadata(workbook);

            Sheet sheet = workbook.createSheet("Exam");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // Tạo hàng tiêu đề (Row 0)
            String[] headers = {
                    "Code đề", "Tên đề", "Thời lượng (phút)", "Loại đề", "Phần",
                    "Loại câu hỏi", "Đề bài tiếng Nhật", "Đề bài tiếng Việt", "Điểm",
                    "Đáp án 1", "Đáp án 2", "Đáp án 3", "Đáp án 4",
                    "Đáp án đúng", "Giải thích"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            boolean firstRow = true;

            for (ExamSection section : paper.getSections()) {
                boolean firstInSection = true;

                for (ExamQuestion question : section.getQuestions()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.setHeightInPoints(20);

                    // Col A: Code đề
                    createCell(row, 0, firstRow ? ExcelCellUtils.sanitizeForExport(paper.getCode()) : "", dataStyle);
                    // Col B: Tên đề
                    createCell(row, 1, firstRow ? ExcelCellUtils.sanitizeForExport(paper.getTitle()) : "", dataStyle);
                    // Col C: Thời lượng
                    createCell(row, 2, firstRow ? String.valueOf(paper.getDurationMinutes()) : "", dataStyle);
                    // Col D: Loại đề
                    createCell(row, 3, firstRow ? (paper.getCategory() != null ? paper.getCategory().name() : "") : "", dataStyle);
                    // Col E: Phần thi
                    createCell(row, 4, firstInSection ? ExcelCellUtils.sanitizeForExport(section.getTitle()) : "", dataStyle);

                    // Col F: Loại câu hỏi
                    createCell(row, 5, question.getType() != null ? question.getType().name() : "", dataStyle);
                    // Col G: Đề bài tiếng Nhật
                    createCell(row, 6, ExcelCellUtils.sanitizeForExport(question.getPromptJa()), dataStyle);
                    // Col H: Đề bài tiếng Việt
                    createCell(row, 7, ExcelCellUtils.sanitizeForExport(question.getPromptVi()), dataStyle);
                    // Col I: Điểm
                    createCell(row, 8, question.getPoints() != null ? question.getPoints().toPlainString() : "1.0", dataStyle);

                    // Choices
                    List<ExamChoice> choices = question.getChoices() != null ? question.getChoices() : Collections.emptyList();
                    createCell(row, 9, choices.size() > 0 ? ExcelCellUtils.sanitizeForExport(choices.get(0).getText()) : "", dataStyle);
                    createCell(row, 10, choices.size() > 1 ? ExcelCellUtils.sanitizeForExport(choices.get(1).getText()) : "", dataStyle);
                    createCell(row, 11, choices.size() > 2 ? ExcelCellUtils.sanitizeForExport(choices.get(2).getText()) : "", dataStyle);
                    createCell(row, 12, choices.size() > 3 ? ExcelCellUtils.sanitizeForExport(choices.get(3).getText()) : "", dataStyle);

                    // Col N: Đáp án đúng
                    String answerStr = formatAnswerForExport(question, choices);
                    createCell(row, 13, ExcelCellUtils.sanitizeForExport(answerStr), dataStyle);

                    // Col O: Giải thích
                    String explanationStr = formatExplanationForExport(question);
                    createCell(row, 14, ExcelCellUtils.sanitizeForExport(explanationStr), dataStyle);

                    firstRow = false;
                    firstInSection = false;
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3500));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    private String formatAnswerForExport(ExamQuestion question, List<ExamChoice> choices) {
        if (question.getType() == ExamQuestionType.MULTIPLE_CHOICE) {
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).isCorrect()) {
                    return String.valueOf(i + 1);
                }
            }
            return "";
        }

        List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : Collections.emptyList();

        if (question.getType() == ExamQuestionType.ORDERING) {
            return keys.stream()
                    .filter(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().startsWith("ORDER:"))
                    .map(k -> k.getAnswerText().substring(6).trim())
                    .findFirst()
                    .orElse("");
        }

        if (question.getType() == ExamQuestionType.TYPING || question.getType() == ExamQuestionType.TRANSLATION) {
            return keys.stream()
                    .filter(k -> k.getKind() == ExamAnswerKind.ACCEPTED)
                    .map(ExamAnswerKey::getAnswerText)
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("");
        }

        if (question.getType() == ExamQuestionType.ESSAY) {
            return keys.stream()
                    .filter(k -> k.getKind() == ExamAnswerKind.SAMPLE)
                    .map(ExamAnswerKey::getAnswerText)
                    .findFirst()
                    .orElse("");
        }

        return "";
    }

    private String formatExplanationForExport(ExamQuestion question) {
        String base = question.getExplanation() != null ? question.getExplanation().trim() : "";
        if (question.getType() == ExamQuestionType.ORDERING) {
            List<ExamAnswerKey> keys = question.getAnswerKeys() != null ? question.getAnswerKeys() : Collections.emptyList();
            String star = keys.stream()
                    .filter(k -> k.getKind() == ExamAnswerKind.ORDER && k.getAnswerText().startsWith("STAR:"))
                    .map(ExamAnswerKey::getAnswerText)
                    .findFirst()
                    .orElse(null);
            if (star != null) {
                return base.isBlank() ? star : star + " | " + base;
            }
        }
        return base;
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Segoe UI");
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Segoe UI");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private void setWorkbookMetadata(XSSFWorkbook workbook) {
        POIXMLProperties props = workbook.getProperties();
        POIXMLProperties.CoreProperties core = props.getCoreProperties();
        core.setCreator("Project Nihon Team");
        core.setTitle("Project Nihon Exam Template");
        POIXMLProperties.ExtendedProperties ext = props.getExtendedProperties();
        ext.setCompany("Project Nihon");
    }

    // =========================================================================
    // 4. Sinh tệp tin Excel mẫu (ExamTemplate.xlsx)
    // =========================================================================

    public byte[] generateTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            setWorkbookMetadata(workbook);

            Sheet sheet = workbook.createSheet("Exam");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            String[] headers = {
                    "Code đề", "Tên đề", "Thời lượng (phút)", "Loại đề", "Phần",
                    "Loại câu hỏi", "Đề bài tiếng Nhật", "Đề bài tiếng Việt", "Điểm",
                    "Đáp án 1", "Đáp án 2", "Đáp án 3", "Đáp án 4",
                    "Đáp án đúng", "Giải thích"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng 1: Trắc nghiệm (MULTIPLE_CHOICE)
            Row r1 = sheet.createRow(1);
            createCell(r1, 0, "N5-EXAM-01", dataStyle);
            createCell(r1, 1, "Đề thi thử JLPT N5 - Đề số 1", dataStyle);
            createCell(r1, 2, "50", dataStyle);
            createCell(r1, 3, "MOCK", dataStyle);
            createCell(r1, 4, "Phần 1: Kiến thức ngôn ngữ (Từ vựng)", dataStyle);
            createCell(r1, 5, "MULTIPLE_CHOICE", dataStyle);
            createCell(r1, 6, "きのう　わたしは　ほんを　（　　）。", dataStyle);
            createCell(r1, 7, "Hôm qua tôi đã (    ) sách.", dataStyle);
            createCell(r1, 8, "1.0", dataStyle);
            createCell(r1, 9, "よみます", dataStyle);
            createCell(r1, 10, "よみました", dataStyle);
            createCell(r1, 11, "よみません", dataStyle);
            createCell(r1, 12, "よむ", dataStyle);
            createCell(r1, 13, "2", dataStyle);
            createCell(r1, 14, "Vì có きのう (hôm qua) nên chia quá khứ lịch sự よみました.", dataStyle);

            // Dòng 2: Gõ câu trả lời (TYPING)
            Row r2 = sheet.createRow(2);
            createCell(r2, 0, "", dataStyle); // Kế thừa hàng trên
            createCell(r2, 1, "", dataStyle);
            createCell(r2, 2, "", dataStyle);
            createCell(r2, 3, "", dataStyle);
            createCell(r2, 4, "", dataStyle); // Kế thừa Phần 1
            createCell(r2, 5, "TYPING", dataStyle);
            createCell(r2, 6, "「ありがとう」の　いみは　なんですか。", dataStyle);
            createCell(r2, 7, "Nghĩa của từ 'Arigatou' là gì?", dataStyle);
            createCell(r2, 8, "1.0", dataStyle);
            createCell(r2, 9, "", dataStyle);
            createCell(r2, 10, "", dataStyle);
            createCell(r2, 11, "", dataStyle);
            createCell(r2, 12, "", dataStyle);
            createCell(r2, 13, "Cảm ơn|Cam on|cảm ơn", dataStyle);
            createCell(r2, 14, "Chấp nhận các cách viết có dấu hoặc không dấu.", dataStyle);

            // Dòng 3: Ghép câu vị trí ngôi sao (ORDERING)
            Row r3 = sheet.createRow(3);
            createCell(r3, 0, "", dataStyle);
            createCell(r3, 1, "", dataStyle);
            createCell(r3, 2, "", dataStyle);
            createCell(r3, 3, "", dataStyle);
            createCell(r3, 4, "Phần 2: Ngữ pháp (Ghép câu)", dataStyle); // Phần mới
            createCell(r3, 5, "ORDERING", dataStyle);
            createCell(r3, 6, "わたしは　＿　＿　★　＿　いきます。", dataStyle);
            createCell(r3, 7, "Tôi đi đến trường bằng xe đạp cùng bạn bè.", dataStyle);
            createCell(r3, 8, "2.0", dataStyle);
            createCell(r3, 9, "がっこうへ", dataStyle);
            createCell(r3, 10, "じてんしゃで", dataStyle);
            createCell(r3, 11, "まいあさ", dataStyle);
            createCell(r3, 12, "ともだちと", dataStyle);
            createCell(r3, 13, "3,2,4,1", dataStyle);
            createCell(r3, 14, "STAR:3 | Vị trí ngôi sao là vị trí thứ 3 (mảnh 4: ともだちと).", dataStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3500));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}
