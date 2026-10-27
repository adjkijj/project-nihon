package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.RoleAuthInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.RowError;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Bộ kiểm thử toàn diện cho ImportController:
 * 1. Không đăng nhập (HTTP 403)
 * 2. Sai role (HTTP 403)
 * 3. Thiếu CSRF (HTTP 403)
 * 4. File sai đuôi (từ chối và thông báo lỗi)
 * 5. Preview đúng (hiển thị báo cáo xem trước và mã tạm 10 phút)
 * 6. Commit đúng (nhập dữ liệu thành công qua mã tạm hoặc file)
 * 7. Vượt giới hạn tần suất 10 lần/phút (HTTP 429 Too Many Requests)
 */
@WebMvcTest(ImportController.class)
@Import({WebConfig.class, CsrfInterceptor.class, RoleAuthInterceptor.class, ImportSessionStorage.class, RateLimiterService.class})
class ImportControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-xyz123";
    private static final byte[] FAKE_ZIP_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ImportSessionStorage sessionStorage;

    @MockitoBean
    private ImportService importService;

    private MockHttpSession adminSession;
    private MockHttpSession teacherSession;
    private MockHttpSession studentSession;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        sessionStorage.clear();

        // 1. Phiên làm việc của Quản trị viên (ADMIN)
        adminSession = new MockHttpSession();
        adminSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ID, "admin-user");
        adminSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ROLE, "ADMIN");
        adminSession.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);

        // 2. Phiên làm việc của Giáo viên (TEACHER)
        teacherSession = new MockHttpSession();
        teacherSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ID, "teacher-user");
        teacherSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ROLE, "TEACHER");
        teacherSession.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);

        // 3. Phiên làm việc của Học viên (STUDENT - không có quyền quản lý import)
        studentSession = new MockHttpSession();
        studentSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ID, "student-user");
        studentSession.setAttribute(RoleAuthInterceptor.SESSION_USER_ROLE, "STUDENT");
        studentSession.setAttribute(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN);
    }

    // =========================================================================
    // 1. KIỂM THỬ KHÔNG ĐĂNG NHẬP -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("1. Không đăng nhập (không có session) truy cập GET /import -> HTTP 403 Forbidden")
    void unauthenticated_getImport_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("1. Không đăng nhập gửi POST /import/preview -> HTTP 403 Forbidden")
    void unauthenticated_preview_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FAKE_ZIP_HEADER);

        mockMvc.perform(multipart("/import/preview").file(file))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. KIỂM THỬ SAI ROLE -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("2. Đăng nhập với role STUDENT truy cập GET /import -> HTTP 403 Forbidden")
    void unauthorizedRole_student_getImport_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/import").session(studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. Đăng nhập với role STUDENT gửi POST /import/preview -> HTTP 403 Forbidden")
    void unauthorizedRole_student_preview_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FAKE_ZIP_HEADER);

        mockMvc.perform(multipart("/import/preview")
                        .file(file)
                        .session(studentSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. Role ADMIN và TEACHER được phép truy cập GET /import -> HTTP 200 OK")
    void authorizedRoles_adminAndTeacher_getImport_returns200Ok() throws Exception {
        mockMvc.perform(get("/import").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("modes"))
                .andExpect(model().attributeExists("selectedMode"));

        mockMvc.perform(get("/import").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(view().name("import"));
    }

    // =========================================================================
    // 3. KIỂM THỬ THIẾU CSRF -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("3. Đăng nhập ADMIN nhưng thiếu CSRF Token gửi POST preview -> HTTP 403 Forbidden")
    void missingCsrfToken_preview_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FAKE_ZIP_HEADER);

        // Không gửi param _csrf hay header X-CSRF-TOKEN
        mockMvc.perform(multipart("/import/preview")
                        .file(file)
                        .session(adminSession))
                .andExpect(status().isForbidden());

        verify(importService, never()).preview(any());
    }

    @Test
    @DisplayName("3. Đăng nhập ADMIN nhưng thiếu CSRF Token gửi POST commit -> HTTP 403 Forbidden")
    void missingCsrfToken_commit_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/import/commit")
                        .session(adminSession)
                        .param("previewToken", "dummy-token"))
                .andExpect(status().isForbidden());

        verify(importService, never()).commit(any(), any(), any());
    }

    // =========================================================================
    // 4. KIỂM THỬ FILE SAI ĐUÔI -> BỊ TỪ CHỐI VÀ BÁO LỖI
    // =========================================================================
    @Test
    @DisplayName("4. Tải lên tệp sai đuôi (.txt) -> Bị từ chối và hiển thị thông báo lỗi rõ ràng")
    void invalidFileExtension_preview_returnsErrorViewWithMessage() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "vocabulary.txt",
                "text/plain",
                "chủ đề,từ vựng,romaji,nghĩa".getBytes(StandardCharsets.UTF_8)
        );

        when(importService.preview(any()))
                .thenThrow(new UploadRejectedException("Chỉ chấp nhận tệp định dạng .xlsx. Tệp tin không hợp lệ: vocabulary.txt"));

        mockMvc.perform(multipart("/import/preview")
                        .file(textFile)
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("errorMessage", containsString("Chỉ chấp nhận tệp định dạng .xlsx")));
    }

    // =========================================================================
    // 5. KIỂM THỬ PREVIEW ĐÚNG -> TRẢ VỀ BÁO CÁO VÀ MÃ TẠM 10 PHÚT
    // =========================================================================
    @Test
    @DisplayName("5. Tải lên file .xlsx hợp lệ -> Xem trước thành công, tạo mã tạm 10 phút")
    void validFile_preview_returnsReportAndPreviewToken() throws Exception {
        MockMultipartFile excelFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        ImportPreviewReport report = new ImportPreviewReport(
                2,  // 2 new topics
                15, // 15 new words
                3,  // 3 duplicate words
                5,  // 5 new kanjis
                0,  // 0 duplicate kanji
                8,  // 8 new kanji words
                0,  // 0 duplicate kanji words
                List.of(new RowError(4, "Cột B", "Cảnh báo định dạng Kanji"))
        );

        when(importService.preview(any())).thenReturn(report);

        mockMvc.perform(multipart("/import/preview")
                        .file(excelFile)
                        .session(adminSession)
                        .header("X-CSRF-TOKEN", CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("previewReport"))
                .andExpect(model().attributeExists("previewToken"))
                .andExpect(model().attribute("fileName", "Update.xlsx"))
                .andExpect(model().attribute("previewReport", is(report)))
                .andExpect(content().string(containsString("Update.xlsx")))
                .andExpect(content().string(containsString("Cảnh báo định dạng Kanji")));
    }

    // =========================================================================
    // 6. KIỂM THỬ COMMIT ĐÚNG -> NHẬP THÀNH CÔNG VÀ HIỂN THỊ KẾT QUẢ
    // =========================================================================
    @Test
    @DisplayName("6. Xác nhận nhập qua mã tạm previewToken -> Commit thành công và ghi nhận kết quả")
    void validToken_commit_returnsSuccessResult() throws Exception {
        // Giả lập bước preview đã lưu tệp tin vào kho phiên làm việc với mã tạm
        MockMultipartFile excelFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );
        String token = sessionStorage.storeFile("admin-user", excelFile);

        ImportCommitResult commitResult = ImportCommitResult.success(2, 15, 3, 0, 5, 0, 0, 8, 0, 0);
        when(importService.commit(any(), eq(ImportMode.SKIP_DUPLICATES), any())).thenReturn(commitResult);

        mockMvc.perform(post("/import/commit")
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("previewToken", token)
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("commitResult"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(content().string(containsString("Nhập dữ liệu thành công")));

        verify(importService).commit(any(), eq(ImportMode.SKIP_DUPLICATES), any());
    }

    @Test
    @DisplayName("6. Xác nhận nhập bằng cách upload trực tiếp tệp tin -> Commit thành công")
    void directFileUpload_commit_returnsSuccessResult() throws Exception {
        MockMultipartFile excelFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        ImportCommitResult commitResult = ImportCommitResult.success(1, 10, 0, 0, 4, 0, 0, 6, 0, 0);
        when(importService.commit(any(), eq(ImportMode.UPDATE_EXISTING), any())).thenReturn(commitResult);

        mockMvc.perform(multipart("/import/commit")
                        .file(excelFile)
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("mode", "UPDATE_EXISTING"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("commitResult"))
                .andExpect(model().attributeExists("successMessage"));

        verify(importService).commit(any(), eq(ImportMode.UPDATE_EXISTING), any());
    }

    @Test
    @DisplayName("6. Mã tạm không tồn tại hoặc đã hết hạn 10 phút -> Từ chối commit và thông báo lỗi")
    void expiredOrInvalidToken_commit_returnsError() throws Exception {
        mockMvc.perform(post("/import/commit")
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("previewToken", "non-existent-expired-token")
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("errorMessage", containsString("Mã xem trước không hợp lệ hoặc đã hết hạn")));

        verify(importService, never()).commit(any(), any(), any());
    }

    // =========================================================================
    // 7. KIỂM THỬ GIỚI HẠN TẦN SUẤT (RATE LIMIT) -> HTTP 429
    // =========================================================================
    @Test
    @DisplayName("7. Vượt quá 10 lần upload trong 1 phút -> Trả về mã lỗi HTTP 429 Too Many Requests")
    void rateLimitExceeded_returns429TooManyRequests() throws Exception {
        MockMultipartFile excelFile = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        ImportPreviewReport report = new ImportPreviewReport(1, 1, 0, 1, 0, 1, 0, List.of());
        when(importService.preview(any())).thenReturn(report);

        // Thực hiện đúng 10 lần tải lên hợp lệ trong 1 phút
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(multipart("/import/preview")
                            .file(excelFile)
                            .session(adminSession)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().isOk());
        }

        // Lần thứ 11: Vượt quá giới hạn tần suất 10 lần/phút -> HTTP 429
        mockMvc.perform(multipart("/import/preview")
                        .file(excelFile)
                        .session(adminSession)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("errorTitle", "429 - Quá giới hạn tần suất"));
    }

    // =========================================================================
    // 8. KIỂM THỬ TẢI FILE MẪU UPDATE.XLSX
    // =========================================================================
    @Test
    @DisplayName("8. Tải tệp tin mẫu Update.xlsx với quyền ADMIN -> HTTP 200 OK kèm header attachment")
    void downloadTemplate_returnsAttachment() throws Exception {
        mockMvc.perform(get("/import/template").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"Update.xlsx\"")))
                .andExpect(header().string("Content-Type", containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }
}
