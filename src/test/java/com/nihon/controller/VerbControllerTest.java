package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.conjugation.ConjugatedForms;
import com.nihon.dto.verb.VerbBulkResultItem;
import com.nihon.dto.verb.VerbDetailDto;
import com.nihon.dto.verb.VerbForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Verb;
import com.nihon.service.TopicService;
import com.nihon.service.VerbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {VerbController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class VerbControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-verb-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerbService verbService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private com.nihon.service.VocabularyExportRateLimiter exportRateLimiter;

    @MockitoBean
    private com.nihon.config.ClientIpResolver clientIpResolver;

    private Topic testTopic;
    private Verb testVerb;
    private VerbDetailDto testVerbDetail;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(10L);
        testTopic.setName("1. Ăn uống");

        testVerb = Verb.builder()
                .id(1L)
                .dictionaryForm("たべる")
                .masuForm("たべます")
                .kanjiText("食べる")
                .verbGroup(2)
                .meaningVi("Ăn cơm")
                .topic(testTopic)
                .build();

        ConjugatedForms forms = new ConjugatedForms(
                "たべる", "たべます", "たべて", "たべた", "たべない",
                "食べる", "食べます", "食べて", "食べた", "食べない"
        );
        testVerbDetail = VerbDetailDto.fromEntity(testVerb, forms);

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        when(exportRateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("1. GET /verbs (List & Search)")
    class ListTests {

        @Test
        @DisplayName("GET /verbs - trả về 200 OK và view verbs/list với đầy đủ model")
        void testListSuccess() throws Exception {
            when(verbService.search(isNull(), isNull(), isNull(), any()))
                    .thenReturn(new PageImpl<>(List.of(testVerb), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/verbs"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/list"))
                    .andExpect(model().attributeExists("verbs"))
                    .andExpect(model().attributeExists("topics"))
                    .andExpect(model().attribute("currentNav", "verbs"))
                    .andExpect(model().attribute("totalCount", 1L));
        }

        @Test
        @DisplayName("GET /verbs với tham số lọc nhóm, chủ đề, từ khóa")
        void testListWithFilters() throws Exception {
            when(verbService.search(eq(2), eq(10L), eq("taberu"), any()))
                    .thenReturn(new PageImpl<>(List.of(testVerb), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/verbs")
                            .param("group", "2")
                            .param("topicId", "10")
                            .param("keyword", "taberu"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/list"))
                    .andExpect(model().attribute("selectedGroup", 2))
                    .andExpect(model().attribute("selectedTopicId", 10L))
                    .andExpect(model().attribute("keyword", "taberu"));
        }

        @Test
        @DisplayName("GET /verbs với phân trang âm hoặc quá lớn - không gây lỗi 500")
        void testListPaginationEdgeCases() throws Exception {
            when(verbService.search(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            // page âm, size âm
            mockMvc.perform(get("/verbs")
                            .param("page", "-5")
                            .param("size", "-10"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/list"));

            // page cực lớn
            mockMvc.perform(get("/verbs")
                            .param("page", "9999999")
                            .param("size", "500"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/list"));
        }
    }

    @Nested
    @DisplayName("2. GET /verbs/{id} (Detail)")
    class DetailTests {

        @Test
        @DisplayName("GET /verbs/1 - tồn tại trả về 200 OK và view verbs/detail với 5 thể chia")
        void testDetailSuccess() throws Exception {
            when(verbService.getDetailById(1L)).thenReturn(testVerbDetail);

            mockMvc.perform(get("/verbs/1"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/detail"))
                    .andExpect(model().attributeExists("verb"))
                    .andExpect(model().attribute("currentNav", "verbs"));
        }

        @Test
        @DisplayName("GET /verbs/0 hoặc ID âm - redirect về /verbs với flash error, không 500")
        void testDetailInvalidId() throws Exception {
            mockMvc.perform(get("/verbs/0"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));

            mockMvc.perform(get("/verbs/-5"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("GET /verbs/999 - không tồn tại redirect về /verbs với flash error")
        void testDetailNotFound() throws Exception {
            when(verbService.getDetailById(999L))
                    .thenThrow(new NoSuchElementException("Không tìm thấy động từ với ID: 999"));

            mockMvc.perform(get("/verbs/999"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attribute("errorMessage", containsString("Không tìm thấy động từ")));
        }

        @Test
        @DisplayName("GET /verbs/abc - ID không phải số được xử lý an toàn (GlobalExceptionHandler redirect, không 500)")
        void testDetailTypeMismatch() throws Exception {
            mockMvc.perform(get("/verbs/abc"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
        }
    }

    @Nested
    @DisplayName("3. GET & POST /verbs/new (Create)")
    class CreateTests {

        @Test
        @DisplayName("GET /verbs/new - hiển thị form tạo mới rỗng")
        void testNewForm() throws Exception {
            mockMvc.perform(get("/verbs/new"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().attributeExists("form"))
                    .andExpect(model().attributeExists("topics"))
                    .andExpect(model().attribute("isEdit", false));
        }

        @Test
        @DisplayName("POST /verbs/new có CSRF và dữ liệu hợp lệ - chuyển hướng tới chi tiết")
        void testCreateSuccessWithCsrf() throws Exception {
            when(verbService.create(any(VerbForm.class))).thenReturn(testVerb);

            mockMvc.perform(post("/verbs/new")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("kanjiText", "食べる")
                            .param("verbGroup", "2")
                            .param("meaningVi", "Ăn cơm")
                            .param("topicId", "10"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs/1"))
                    .andExpect(flash().attributeExists("successMessage"));

            verify(verbService).create(any(VerbForm.class));
        }

        @Test
        @DisplayName("POST /verbs/new KHÔNG có CSRF - trả về 403 Forbidden")
        void testCreateForbiddenWithoutCsrf() throws Exception {
            mockMvc.perform(post("/verbs/new")
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("verbGroup", "2")
                            .param("meaningVi", "Ăn cơm"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /verbs/new vi phạm Bean Validation - trả về 200 form có errors")
        void testCreateValidationErrors() throws Exception {
            mockMvc.perform(post("/verbs/new")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "") // rỗng
                            .param("masuForm", "invalid") // không kết thúc bằng ます
                            .param("verbGroup", "5") // ngoài 1..3
                            .param("meaningVi", ""))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrors("form", "dictionaryForm", "masuForm", "verbGroup", "meaningVi"));
        }

        @Test
        @DisplayName("POST /verbs/new lỗi tính nhất quán (Consistency) - trả về form kèm errorMessage")
        void testCreateConsistencyError() throws Exception {
            when(verbService.create(any(VerbForm.class)))
                    .thenThrow(new IllegalArgumentException(VerbService.CONSISTENCY_ERROR_MESSAGE));

            mockMvc.perform(post("/verbs/new")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "のむ")
                            .param("masuForm", "のめます")
                            .param("kanjiText", "飲む")
                            .param("verbGroup", "1")
                            .param("meaningVi", "Uống"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().attribute("errorMessage", VerbService.CONSISTENCY_ERROR_MESSAGE));
        }

        @Test
        @DisplayName("POST /verbs/new lỗi trùng động từ (Unique) - trả về form kèm errorMessage")
        void testCreateDuplicateError() throws Exception {
            when(verbService.create(any(VerbForm.class)))
                    .thenThrow(new IllegalArgumentException("Động từ 'たべる (食べる)' đã tồn tại trong hệ thống."));

            mockMvc.perform(post("/verbs/new")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("kanjiText", "食べる")
                            .param("verbGroup", "2")
                            .param("meaningVi", "Ăn"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().attribute("errorMessage", containsString("đã tồn tại trong hệ thống")));
        }
    }

    @Nested
    @DisplayName("4. GET & POST /verbs/{id}/edit (Update)")
    class EditTests {

        @Test
        @DisplayName("GET /verbs/1/edit - hiển thị form sửa với thông tin có sẵn")
        void testEditFormSuccess() throws Exception {
            when(verbService.getById(1L)).thenReturn(testVerb);

            mockMvc.perform(get("/verbs/1/edit"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().attribute("isEdit", true))
                    .andExpect(model().attribute("verbId", 1L))
                    .andExpect(model().attributeExists("form"));
        }

        @Test
        @DisplayName("GET /verbs/999/edit - không tồn tại redirect về /verbs")
        void testEditFormNotFound() throws Exception {
            when(verbService.getById(999L))
                    .thenThrow(new NoSuchElementException("Không tìm thấy động từ với ID: 999"));

            mockMvc.perform(get("/verbs/999/edit"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("POST /verbs/1/edit có CSRF và dữ liệu hợp lệ - chuyển hướng tới chi tiết")
        void testUpdateSuccess() throws Exception {
            when(verbService.update(eq(1L), any(VerbForm.class))).thenReturn(testVerb);

            mockMvc.perform(post("/verbs/1/edit")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("kanjiText", "食べる")
                            .param("verbGroup", "2")
                            .param("meaningVi", "Ăn ngon")
                            .param("topicId", "10"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs/1"))
                    .andExpect(flash().attributeExists("successMessage"));

            verify(verbService).update(eq(1L), any(VerbForm.class));
        }

        @Test
        @DisplayName("POST /verbs/1/edit KHÔNG có CSRF - 403 Forbidden")
        void testUpdateForbiddenWithoutCsrf() throws Exception {
            mockMvc.perform(post("/verbs/1/edit")
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("verbGroup", "2")
                            .param("meaningVi", "Ăn"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /verbs/1/edit lỗi tính nhất quán - trả về form kèm errorMessage")
        void testUpdateConsistencyError() throws Exception {
            when(verbService.update(eq(1L), any(VerbForm.class)))
                    .thenThrow(new IllegalArgumentException(VerbService.CONSISTENCY_ERROR_MESSAGE));

            mockMvc.perform(post("/verbs/1/edit")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("dictionaryForm", "たべる")
                            .param("masuForm", "たべます")
                            .param("kanjiText", "食べる")
                            .param("verbGroup", "1") // sai nhóm
                            .param("meaningVi", "Ăn"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/form"))
                    .andExpect(model().attribute("errorMessage", VerbService.CONSISTENCY_ERROR_MESSAGE));
        }
    }

    @Nested
    @DisplayName("5. POST /verbs/{id}/delete (Delete)")
    class DeleteTests {

        @Test
        @DisplayName("POST /verbs/1/delete có CSRF - xóa thành công và redirect /verbs")
        void testDeleteSuccess() throws Exception {
            doNothing().when(verbService).delete(1L);

            mockMvc.perform(post("/verbs/1/delete")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("successMessage"));

            verify(verbService).delete(1L);
        }

        @Test
        @DisplayName("POST /verbs/1/delete KHÔNG có CSRF - 403 Forbidden")
        void testDeleteForbiddenWithoutCsrf() throws Exception {
            mockMvc.perform(post("/verbs/1/delete"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /verbs/0/delete - ID không hợp lệ redirect /verbs kèm errorMessage")
        void testDeleteInvalidId() throws Exception {
            mockMvc.perform(post("/verbs/0/delete")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("POST /verbs/999/delete - lỗi khi xóa redirect /verbs kèm errorMessage")
        void testDeleteError() throws Exception {
            doThrow(new NoSuchElementException("Không tìm thấy động từ"))
                    .when(verbService).delete(999L);

            mockMvc.perform(post("/verbs/999/delete")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }
    }

    @Nested
    @DisplayName("6. GET & POST /verbs/bulk (Bulk Add)")
    class BulkTests {

        @Test
        @DisplayName("GET /verbs/bulk - hiển thị form nhập hàng loạt")
        void testBulkForm() throws Exception {
            mockMvc.perform(get("/verbs/bulk"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/bulk"))
                    .andExpect(model().attributeExists("bulkForm"))
                    .andExpect(model().attributeExists("topics"));
        }

        @Test
        @DisplayName("POST /verbs/bulk có CSRF và dữ liệu hợp lệ - trả về kết quả dòng thành công")
        void testBulkProcessSuccess() throws Exception {
            List<VerbBulkResultItem> results = List.of(
                    VerbBulkResultItem.builder()
                            .lineNumber(1)
                            .line("たべます,Ăn")
                            .success(true)
                            .message("Thành công: Nhóm 2 (たべる)")
                            .verb(testVerb)
                            .build()
            );

            when(verbService.bulkAdd(anyString(), any())).thenReturn(results);

            mockMvc.perform(post("/verbs/bulk")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("text", "たべます,Ăn")
                            .param("topicId", "10"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/bulk"))
                    .andExpect(model().attributeExists("results"))
                    .andExpect(model().attribute("successCount", 1L))
                    .andExpect(model().attribute("totalCount", 1));
        }

        @Test
        @DisplayName("POST /verbs/bulk KHÔNG có CSRF - 403 Forbidden")
        void testBulkForbiddenWithoutCsrf() throws Exception {
            mockMvc.perform(post("/verbs/bulk")
                            .param("text", "たべます,Ăn"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /verbs/bulk vượt quá 100 dòng - ném IllegalArgumentException hiển thị errorMessage")
        void testBulkOverLimit() throws Exception {
            when(verbService.bulkAdd(anyString(), any()))
                    .thenThrow(new IllegalArgumentException("Dữ liệu hàng loạt không được vượt quá 100 dòng."));

            mockMvc.perform(post("/verbs/bulk")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                            .param("text", "101 dòng..."))
                    .andExpect(status().isOk())
                    .andExpect(view().name("verbs/bulk"))
                    .andExpect(model().attribute("errorMessage", containsString("100 dòng")));
        }
    }

    @Nested
    @DisplayName("7. POST /verbs/sample-data (Sample Data)")
    class SampleDataTests {

        @Test
        @DisplayName("POST /verbs/sample-data có CSRF khi rỗng - nạp thành công và redirect /verbs")
        void testSampleDataSuccess() throws Exception {
            when(verbService.loadSampleData()).thenReturn(List.of(testVerb));

            mockMvc.perform(post("/verbs/sample-data")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attribute("successMessage", containsString("5 động từ mẫu")));

            verify(verbService).loadSampleData();
        }

        @Test
        @DisplayName("POST /verbs/sample-data KHÔNG có CSRF - 403 Forbidden")
        void testSampleDataForbiddenWithoutCsrf() throws Exception {
            mockMvc.perform(post("/verbs/sample-data"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /verbs/sample-data khi DB không rỗng - redirect /verbs kèm errorMessage")
        void testSampleDataNotEmpty() throws Exception {
            doThrow(new IllegalStateException("Chỉ có thể nạp dữ liệu mẫu khi danh sách động từ đang trống."))
                    .when(verbService).loadSampleData();

            mockMvc.perform(post("/verbs/sample-data")
                            .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                            .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/verbs"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }
    }
}
