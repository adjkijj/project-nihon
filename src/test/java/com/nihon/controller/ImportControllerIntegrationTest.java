package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.entity.ImportAuditLog;
import com.nihon.entity.KanjiEntry;
import com.nihon.repository.ImportAuditLogRepository;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.KanjiWordRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * không cần tài khoản hay vai trò người dùng (account-free import).
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

    private byte[] updateXlsxBytes;

    @BeforeEach
    void setUp() throws Exception {
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
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
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
        assertThat(previewToken).isNotBlank();

        // Giai đoạn 2: Xác nhận nhập dữ liệu (Commit) sử dụng mã tạm 10 phút
        MvcResult commitResult = mockMvc.perform(post("/import/commit")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
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

        // Giai đoạn 3: Kiểm tra ghi nhận Audit Log
        List<ImportAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        ImportAuditLog lastLog = logs.get(logs.size() - 1);
        assertThat(lastLog.getFileName()).isEqualTo("Update.xlsx");
        assertThat(lastLog.getUserId()).isEqualTo("127.0.0.1");
    }
}
