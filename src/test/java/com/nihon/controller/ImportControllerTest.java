package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.ImportSecurityInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.RowError;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
 * 1. GET /import không cần tài khoản trả 200 OK và hiển thị form upload
 * 2. Thiếu CSRF Token trả HTTP 403 Forbidden
 * 3. File sai đuôi (.txt) bị từ chối và báo lỗi
 * 4. Preview đúng hiển thị báo cáo xem trước và mã tạm 10 phút
 * 5. Commit đúng nhập dữ liệu thành công qua mã tạm hoặc file
 * 6. Vượt giới hạn tần suất 10 lần/phút nhận HTTP 429 Too Many Requests
 * 7. IP ngoài danh sách allowed-ips nhận HTTP 403 Forbidden
 */
@WebMvcTest(ImportController.class)
@Import({WebConfig.class, CsrfInterceptor.class, ImportSecurityInterceptor.class, ClientIpResolver.class, ImportSessionStorage.class, RateLimiterService.class})
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

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        sessionStorage.clear();
    }

    // =========================================================================
    // 1. KIỂM THỬ GET /import KHÔNG CẦN TÀI KHOẢN -> 200 OK
    // =========================================================================
    @Test
    @DisplayName("1. Truy cập GET /import không cần tài khoản hay role -> HTTP 200 OK")
    void unauthenticated_getImport_returns200Ok() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("modes"))
                .andExpect(model().attributeExists("selectedMode"))
                .andExpect(content().string(containsString("previewForm")));
    }

    // =========================================================================
    // 2. KIỂM THỬ THIẾU CSRF -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("2. Thiếu CSRF Token gửi POST preview -> HTTP 403 Forbidden")
    void missingCsrfToken_preview_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FAKE_ZIP_HEADER);

        // Không gửi param _csrf hay header X-CSRF-TOKEN
        mockMvc.perform(multipart("/import/preview").file(file))
                .andExpect(status().isForbidden());

        verify(importService, never()).preview(any());
    }

    @Test
    @DisplayName("2. Thiếu CSRF Token gửi POST commit -> HTTP 403 Forbidden")
    void missingCsrfToken_commit_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/import/commit")
                        .param("previewToken", "dummy-token"))
                .andExpect(status().isForbidden());

        verify(importService, never()).commit(any(), any(), any());
    }

    // =========================================================================
    // 3. KIỂM THỬ FILE SAI ĐUÔI -> BỊ TỪ CHỐI VÀ BÁO LỖI
    // =========================================================================
    @Test
    @DisplayName("3. Tải lên tệp sai đuôi (.txt) -> Bị từ chối và hiển thị thông báo lỗi rõ ràng")
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
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("errorMessage", containsString("Chỉ chấp nhận tệp định dạng .xlsx")));
    }

    // =========================================================================
    // 4. KIỂM THỬ PREVIEW ĐÚNG -> TRẢ VỀ BÁO CÁO VÀ MÃ TẠM 10 PHÚT
    // =========================================================================
    @Test
    @DisplayName("4. Tải lên file .xlsx hợp lệ -> Xem trước thành công, tạo mã tạm 10 phút")
    void validFile_preview_returnsReportAndPreviewToken() throws Exception {
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        ImportPreviewReport mockReport = new ImportPreviewReport(
                2, 10, 2, 4, 1, 3, 1, List.of()
        );
        when(importService.preview(any())).thenReturn(mockReport);

        mockMvc.perform(multipart("/import/preview")
                        .file(validFile)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("previewReport"))
                .andExpect(model().attributeExists("previewToken"))
                .andExpect(model().attribute("fileName", "Update.xlsx"))
                .andExpect(content().string(containsString("Bước 2: Kết Quả Xem Trước")))
                .andExpect(content().string(containsString("Bước 3: Lựa Chọn Chế Độ &amp; Xác Nhận Nhập")));
    }

    // =========================================================================
    // 5. KIỂM THỬ COMMIT ĐÚNG -> NHẬP DỮ LIỆU THÀNH CÔNG
    // =========================================================================
    @Test
    @DisplayName("5. Xác nhận nhập qua mã tạm hợp lệ -> Ghi dữ liệu thành công")
    void commit_withValidPreviewToken_success() throws Exception {
        MockMultipartFile storedFile = new MockMultipartFile(
                "file", "Update.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );
        // Lưu trữ mã tạm với clientIp "127.0.0.1" (remoteAddr mặc định của MockMvc)
        String previewToken = sessionStorage.storeFile("127.0.0.1", storedFile);

        ImportCommitResult commitResult = ImportCommitResult.success(
                1, 10, 0, 0, 4, 0, 0, 3, 0, 0
        );
        when(importService.commit(any(), eq(ImportMode.SKIP_DUPLICATES), any())).thenReturn(commitResult);

        mockMvc.perform(post("/import/commit")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("previewToken", previewToken)
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attributeExists("commitResult"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(content().string(containsString("Nhập dữ liệu thành công")));

        verify(importService).commit(any(), eq(ImportMode.SKIP_DUPLICATES), any());
    }

    // =========================================================================
    // 6. KIỂM THỬ GIỚI HẠN TẦN SUẤT -> HTTP 429
    // =========================================================================
    @Test
    @DisplayName("6. Tải lên quá 10 lần trong 1 phút -> Trả về HTTP 429 Too Many Requests")
    void rateLimiter_exceedLimit_returns429TooManyRequests() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FAKE_ZIP_HEADER);

        ImportPreviewReport dummyReport = new ImportPreviewReport(1, 1, 0, 0, 0, 0, 0, List.of());
        when(importService.preview(any())).thenReturn(dummyReport);

        // Gửi 10 request hợp lệ đầu tiên
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(multipart("/import/preview")
                            .file(file)
                            .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().isOk());
        }

        // Lần thứ 11 vượt quá hạn ngạch -> HTTP 429
        mockMvc.perform(multipart("/import/preview")
                        .file(file)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Bạn đã vượt quá giới hạn tải lên")));
    }

    // =========================================================================
    // 7. KIỂM THỬ IP NGOÀI DANH SÁCH ALLOWED-IPS -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("7. Địa chỉ IP ngoài danh sách allowed-ips -> HTTP 403 Forbidden")
    void ipOutsideAllowedIps_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/import")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.25");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. TẢI FILE MẪU UPDATE.XLSX -> HTTP 200
    // =========================================================================
    @Test
    @DisplayName("8. Tải tệp tin mẫu Update.xlsx qua GET /import/template -> HTTP 200 OK kèm header attachment")
    void downloadTemplate_returnsFileAttachment() throws Exception {
        mockMvc.perform(get("/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", is("attachment; filename=\"Update.xlsx\"")));
    }
}
