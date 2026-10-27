package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.RoleAuthInterceptor;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tích hợp toàn trình (End-to-End Integration Test) cho ImportController
 * sử dụng trực tiếp tệp mẫu Update.xlsx thực tế với cơ sở dữ liệu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerIntegrationTest {

    private static final String CSRF_TOKEN = "integration-test-csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private KanjiEntryRepository kanjiEntryRepository;

    @Autowired
    private KanjiWordRepository kanjiWordRepository;

    @Autowired
    private ImportAuditLogRepository auditLogRepository;

    private MockHttpSession adminSession;
    private byte[] updateXlsxBytes;

    @BeforeEach
    void setUp() throws Exception {
        adminSession = new MockHttpSession();
        adminSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ID, "admin-e2e");
        adminSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ROLE, "ADMIN");
        adminSession.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);

        File excelFile = new File("src/main/resources/static/template/Update.xlsx");
        if (!excelFile.exists()) {
            excelFile = new File("Update.xlsx");
        }
        assertThat(excelFile).exists();
        updateXlsxBytes = Files.readAllBytes(excelFile.toPath());
    }

    @Test
    @DisplayName("Toàn trình: Preview Update.xlsx -> nhận mã tạm -> Commit qua mã tạm -> Dữ liệu lưu đúng cấu trúc")
    void fullImportFlow_withUpdateXlsx_success() throws Exception {
        // Giai đoạn 1: Tải lên xem trước (Preview)
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                updateXlsxBytes
        );

        MvcResult previewResult = mockMvc.perform(multipart("/import/preview")
                        .file(multipartFile)
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("previewReport"))
                .andExpect(model().attributeExists("previewToken"))
                .andExpect(content().string(containsString("Update.xlsx")))
                .andReturn();

        ImportPreviewReport previewReport = (ImportPreviewReport) previewResult.getModelAndView().getModel().get("previewReport");
        String previewToken = (String) previewResult.getModelAndView().getModel().get("previewToken");

        assertThat(previewReport).isNotNull();
        assertThat(previewReport.hasErrors()).isFalse();
        // File Update.xlsx: Sheet "New Word" có 2 chủ đề, 6 từ vựng; Sheet "Kanji" có 2 chủ đề, 3 Kanji ('波', '橋', '嵐') và 4 từ ghép
        assertThat(previewReport.newWordsCount() + previewReport.duplicateWordsCount()).isEqualTo(6);
        assertThat(previewReport.newKanjiCount() + previewReport.duplicateKanjiCount()).isEqualTo(3);
        assertThat(previewReport.newKanjiWordsCount() + previewReport.duplicateKanjiWordsCount()).isEqualTo(4);
        assertThat(previewToken).isNotBlank();

        // Giai đoạn 2: Xác nhận nhập dữ liệu (Commit) sử dụng mã tạm 10 phút
        MvcResult commitResult = mockMvc.perform(post("/import/commit")
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("previewToken", previewToken)
                        .param("mode", ImportMode.UPDATE_EXISTING.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("commitResult"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(content().string(containsString("Nhập dữ liệu thành công")))
                .andReturn();

        ImportCommitResult result = (ImportCommitResult) commitResult.getModelAndView().getModel().get("commitResult");
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();

        // Giai đoạn 3: Kiểm tra tính đúng đắn dữ liệu đã lưu trong Database
        // Kiểm tra 3 Kanji mới: 波, 橋, 嵐
        assertThat(kanjiEntryRepository.findByCharacter("波")).isPresent();
        assertThat(kanjiEntryRepository.findByCharacter("橋")).isPresent();
        assertThat(kanjiEntryRepository.findByCharacter("嵐")).isPresent();

        // Kiểm tra Kanji "波" (BA) và các từ ghép
        KanjiEntry baKanji = kanjiEntryRepository.findByCharacter("波").orElseThrow();
        assertThat(baKanji.getMeaningVi()).containsIgnoringCase("làn sóng");
        assertThat(kanjiWordRepository.findByKanjiIdOrderBySortOrderAsc(baKanji.getId())).hasSize(3);

        // Kiểm tra ghi nhận Audit Log
        List<ImportAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        ImportAuditLog lastLog = logs.get(logs.size() - 1);
        assertThat(lastLog.getFileName()).isEqualTo("Update.xlsx");
        assertThat(lastLog.getUserId()).isEqualTo("admin-e2e");
    }
}
