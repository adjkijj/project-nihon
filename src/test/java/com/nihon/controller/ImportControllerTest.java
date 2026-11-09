package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.ImportSecurityInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.excel.ImportCommitResult;
import com.nihon.dto.excel.ImportMode;
import com.nihon.dto.excel.ImportPreviewReport;
import com.nihon.dto.excel.RowError;
import com.nihon.entity.ImportAuditLog;
import com.nihon.service.excel.ImportService;
import com.nihon.service.excel.ImportSessionStorage;
import com.nihon.service.excel.RateLimiterService;
import com.nihon.service.excel.UploadRejectedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    // =========================================================================
    // 9. PREVIEW VỚI FILE HỎNG KHÔNG TRẢ NỘI DUNG ex.getMessage()
    // =========================================================================
    @Test
    @DisplayName("9. Preview với file hỏng ném RuntimeException không lộ ex.getMessage() ra giao diện")
    void corruptedFile_preview_doesNotExposeExceptionMessage() throws Exception {
        MockMultipartFile corruptedFile = new MockMultipartFile(
                "file",
                "corrupted.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        when(importService.preview(any()))
                .thenThrow(new RuntimeException("INTERNAL_SECRET_POI_STACK_DETAIL_XYZ"));

        mockMvc.perform(multipart("/import/preview")
                        .file(corruptedFile)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attribute("errorMessage", "Không thể đọc tệp tin. Vui lòng kiểm tra định dạng và thử lại."))
                .andExpect(content().string(containsString("Không thể đọc tệp tin. Vui lòng kiểm tra định dạng và thử lại.")))
                .andExpect(content().string(not(containsString("INTERNAL_SECRET_POI_STACK_DETAIL_XYZ"))));
    }

    // =========================================================================
    // 10. PREVIEW TOKEN SAI MẪU UUID VÀ LÀM SẠCH TÊN FILE HIỂN THỊ
    // =========================================================================
    @Test
    @DisplayName("10. Commit với previewToken sai mẫu UUID báo 'Mã xem trước không hợp lệ' ngay")
    void commit_withInvalidPreviewTokenFormat_returnsErrorImmediately() throws Exception {
        mockMvc.perform(post("/import/commit")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("previewToken", "not-a-valid-uuid-token")
                        .param("mode", "SKIP_DUPLICATES"))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attribute("errorMessage", "Mã xem trước không hợp lệ"));

        verify(importService, never()).commit(any(), any(), any());
    }

    @Test
    @DisplayName("11. Tên file hiển thị khi preview bỏ đường dẫn, loại ký tự điều khiển và cắt tối đa 100 ký tự")
    void preview_sanitizesDisplayFileName() throws Exception {
        String longNameWithControlAndPath = "C:\\fakepath\\..\\subdir/file\u0000\u0007\r\n_" + "a".repeat(120) + ".xlsx";
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                longNameWithControlAndPath,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FAKE_ZIP_HEADER
        );

        ImportPreviewReport mockReport = new ImportPreviewReport(1, 1, 0, 0, 0, 0, 0, List.of());
        when(importService.preview(any())).thenReturn(mockReport);

        String expectedSanitized = ("file_" + "a".repeat(120) + ".xlsx").substring(0, 100);

        mockMvc.perform(multipart("/import/preview")
                        .file(validFile)
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("import"))
                .andExpect(model().attribute("fileName", expectedSanitized));
    }

    // =========================================================================
    // 12. LỊCH SỬ IMPORT (/import/history): MỚI NHẤT TRƯỚC, HASH 12 KÝ TỰ, CẮT LỖI 300 KÝ TỰ
    // =========================================================================
    @Test
    @DisplayName("12. GET /import/history hiện đúng thứ tự mới nhất trước, rút gọn hash 12 ký tự và cắt errorMessage tối đa 300 ký tự")
    void history_displaysNewestFirst_shortHash12Chars_andTruncatesErrorMessageTo300Chars() throws Exception {
        ImportAuditLog newer = new ImportAuditLog();
        newer.setId(2L);
        newer.setUserId("127.0.0.1");
        newer.setImportedAt(LocalDateTime.of(2026, 11, 6, 15, 30, 0));
        newer.setFileName("Newer_Update.xlsx");
        newer.setFileHashSha256("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        newer.setRecordsAdded(15);
        newer.setRecordsUpdated(3);
        newer.setRecordsSkipped(2);
        newer.setResult("SUCCESS");
        newer.setErrorMessage(null);

        ImportAuditLog older = new ImportAuditLog();
        older.setId(1L);
        older.setUserId("192.168.1.10");
        older.setImportedAt(LocalDateTime.of(2026, 11, 5, 9, 15, 0));
        older.setFileName("Older_Failed.xlsx");
        older.setFileHashSha256("9988776655443322110099887766554433221100998877665544332211009988");
        older.setRecordsAdded(0);
        older.setRecordsUpdated(0);
        older.setRecordsSkipped(0);
        older.setResult("FAILED");
        older.setErrorMessage("E".repeat(350));

        when(importService.findHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newer, older)));

        String html = mockMvc.perform(get("/import/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("import-history"))
                .andExpect(model().attributeExists("historyPage"))
                .andExpect(content().string(containsString("abcdef123456")))
                .andExpect(content().string(containsString("998877665544")))
                .andExpect(content().string(containsString("E".repeat(300))))
                .andExpect(content().string(not(containsString("E".repeat(301)))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Mới nhất đứng trước bản ghi cũ hơn
        assertThat(html.indexOf("Newer_Update.xlsx")).isLessThan(html.indexOf("Older_Failed.xlsx"));

        // Kiểm tra Pageable yêu cầu 20 dòng/trang và sắp xếp importedAt DESC
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(importService).findHistory(pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageSize()).isEqualTo(20);
        Sort.Order importedAtOrder = captured.getSort().getOrderFor("importedAt");
        assertThat(importedAtOrder).isNotNull();
        assertThat(importedAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // =========================================================================
    // 13. LỊCH SỬ IMPORT: IP NGOÀI DANH SÁCH BỊ 403, app.import.enabled=false TRẢ 404
    // =========================================================================
    @Test
    @DisplayName("13a. GET /import/history với IP ngoài danh sách allowed-ips bị chặn 403 Forbidden")
    void history_ipOutsideAllowedIps_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/import/history")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.99");
                            return request;
                        }))
                .andExpect(status().isForbidden());

        verify(importService, never()).findHistory(any());
    }

    @Test
    @DisplayName("13b. GET /import/history khi app.import.enabled=false trả về 404 Not Found")
    void history_whenImportDisabled_returns404NotFound() throws Exception {
        ImportSecurityInterceptor disabledInterceptor = new ImportSecurityInterceptor(
                false, "127.0.0.1", new ClientIpResolver("127.0.0.1"), "127.0.0.1");
        ImportController controller = new ImportController(
                importService, sessionStorage, rateLimiterService, new ClientIpResolver("127.0.0.1"), null);
        MockMvc disabledMockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .addInterceptors(disabledInterceptor)
                .build();

        disabledMockMvc.perform(get("/import/history"))
                .andExpect(status().isNotFound());

        verify(importService, never()).findHistory(any());
    }

    // =========================================================================
    // 14. PHÂN TRANG VỚI PAGE QUÁ LỚN KHÔNG LỖI 500
    // =========================================================================
    @Test
    @DisplayName("14. GET /import/history với page quá lớn hoặc âm không lỗi 500")
    void history_hugeOrNegativePage_clampsPageAndReturns200() throws Exception {
        when(importService.findHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/import/history").param("page", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(view().name("import-history"));

        mockMvc.perform(get("/import/history").param("page", "-50"))
                .andExpect(status().isOk())
                .andExpect(view().name("import-history"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(importService, org.mockito.Mockito.times(2)).findHistory(pageableCaptor.capture());
        List<Pageable> calls = pageableCaptor.getAllValues();
        assertThat(calls.get(0).getPageNumber()).isEqualTo(100000);
        assertThat(calls.get(1).getPageNumber()).isZero();
    }

    // =========================================================================
    // 15. KHÔNG CÓ ENDPOINT POST TRÊN /import/history
    // =========================================================================
    @Test
    @DisplayName("15. Không có endpoint POST nào trên /import/history (trả về 405 Method Not Allowed)")
    void history_postRequest_returns405MethodNotAllowed() throws Exception {
        mockMvc.perform(post("/import/history")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isMethodNotAllowed());
    }

    // =========================================================================
    // 16. XUẤT DỮ LIỆU EXCEL (GET /import/export)
    // =========================================================================
    @Test
    @DisplayName("16a. GET /import/export trả về file .xlsx với đúng header Content-Disposition, Content-Type và Cache-Control")
    void exportExcel_returnsXlsxWithProperHeaders() throws Exception {
        byte[] dummyXlsx = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(importService.exportToExcel()).thenReturn(dummyXlsx);

        String contentDisposition = mockMvc.perform(get("/import/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(dummyXlsx))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(contentDisposition).matches("attachment; filename=\"nihon-export-\\d{8}-\\d{4}\\.xlsx\"");
    }

    @Test
    @DisplayName("16b. GET /import/export khi vượt giới hạn 50000 hàng trả về 400 Bad Request với thông báo rõ ràng")
    void exportExcel_whenExceedsMaxRows_returns400WithClearMessage() throws Exception {
        when(importService.exportToExcel())
                .thenThrow(new IllegalStateException("Số lượng hàng trên sheet 'New Word' vượt quá giới hạn tối đa 50000 hàng."));

        mockMvc.perform(get("/import/export"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("50000")));
    }

    @Test
    @DisplayName("16c. GET /import/export đi qua ImportSecurityInterceptor và RateLimiterService")
    void exportExcel_enforcesIpSecurityAndRateLimit() throws Exception {
        // IP ngoài danh sách -> 403 Forbidden
        mockMvc.perform(get("/import/export")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.99");
                            return request;
                        }))
                .andExpect(status().isForbidden());

        verify(importService, never()).exportToExcel();

        // Tiêu thụ hết 10 lượt/phút của IP 127.0.0.1 -> lần thứ 11 trả 429 Too Many Requests
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiterService.tryAcquire("127.0.0.1")).isTrue();
        }
        mockMvc.perform(get("/import/export"))
                .andExpect(status().isTooManyRequests());

        verify(importService, never()).exportToExcel();
    }

    // =========================================================================
    // 17. HOÀN TÁC IMPORT (POST /import/history/{id}/undo) & GIAO DIỆN NÚT HOÀN TÁC
    // =========================================================================
    @Test
    @DisplayName("17a. POST /import/history/{id}/undo thiếu CSRF Token bị chặn 403 Forbidden")
    void undoImport_missingCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/import/history/10/undo"))
                .andExpect(status().isForbidden());

        verify(importService, never()).undoImport(any(), any());
    }

    @Test
    @DisplayName("17b. POST /import/history/{id}/undo có CSRF hợp lệ thực hiện hoàn tác và chuyển hướng về /import/history")
    void undoImport_withValidCsrf_redirectsWithSuccessMessage() throws Exception {
        com.nihon.dto.excel.ImportUndoResult undoResult = new com.nihon.dto.excel.ImportUndoResult(
                10L, 5, 2, 1, 0, 1, Collections.emptyList(), 99L
        );
        when(importService.undoImport(eq(10L), anyString())).thenReturn(undoResult);

        mockMvc.perform(post("/import/history/10/undo")
                        .cookie(new Cookie(CsrfInterceptor.CSRF_COOKIE_NAME, CSRF_TOKEN))
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection());

        verify(importService).undoImport(eq(10L), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("17c. GET /import/history hiện nút Hoàn tác kèm data-confirm xem trước số lượng bản ghi xóa khi chưa hoàn tác và ẩn khi đã hoàn tác")
    void history_rendersUndoButtonWhenNotUndoneAndHidesWhenUndone() throws Exception {
        ImportAuditLog activeSuccess = new ImportAuditLog();
        activeSuccess.setId(10L);
        activeSuccess.setUserId("127.0.0.1");
        activeSuccess.setImportedAt(LocalDateTime.of(2026, 11, 9, 4, 30, 0));
        activeSuccess.setFileName("Active_Batch.xlsx");
        activeSuccess.setFileHashSha256("a".repeat(64));
        activeSuccess.setRecordsAdded(8);
        activeSuccess.setResult("SUCCESS");
        activeSuccess.setUndoneAt(null);

        ImportAuditLog alreadyUndone = new ImportAuditLog();
        alreadyUndone.setId(9L);
        alreadyUndone.setUserId("127.0.0.1");
        alreadyUndone.setImportedAt(LocalDateTime.of(2026, 11, 9, 4, 0, 0));
        alreadyUndone.setFileName("Undone_Batch.xlsx");
        alreadyUndone.setFileHashSha256("b".repeat(64));
        alreadyUndone.setRecordsAdded(4);
        alreadyUndone.setResult("SUCCESS");
        alreadyUndone.setUndoneAt(LocalDateTime.of(2026, 11, 9, 4, 15, 0));

        when(importService.findHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeSuccess, alreadyUndone)));

        com.nihon.dto.excel.ImportUndoPreview preview10 = new com.nihon.dto.excel.ImportUndoPreview(
                10L, 5L, 2L, 1L, 0L, 0L, Collections.emptyList()
        );
        when(importService.getUndoPreviews(any())).thenReturn(java.util.Map.of(10L, preview10));

        mockMvc.perform(get("/import/history"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/import/history/10/undo")))
                .andExpect(content().string(containsString("Sẽ xóa 8 bản ghi (5 từ vựng, 2 Kanji, 1 chủ đề)")))
                .andExpect(content().string(not(containsString("/import/history/9/undo"))))
                .andExpect(content().string(containsString("Đã hoàn tác")));
    }
}
