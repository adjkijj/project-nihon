package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.entity.GrammarCategory;
import com.nihon.entity.GrammarTopic;
import com.nihon.repository.GrammarTopicRepository;
import com.nihon.service.grammar.GrammarExcelService;
import com.nihon.service.grammar.GrammarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GrammarAdminIntegrationTest {

    private static final String CSRF_TOKEN = "test-csrf-grammar-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private GrammarExcelService excelService;

    @Autowired
    private GrammarTopicRepository topicRepository;

    @Test
    @DisplayName("GET /grammar: Hiển thị giao diện học ngữ pháp cho người học (Prompt 23)")
    void testPublicGrammarRouteRendersLearnerPage() throws Exception {
        mockMvc.perform(get("/grammar"))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/index"));
    }

    @Test
    @DisplayName("Tiêu chí: Tạo một chủ đề ngữ pháp bằng giao diện (UI Form)")
    void testCreateGrammarTopicViaForm() throws Exception {
        // 1. Mở trang tạo mới
        mockMvc.perform(get("/grammar/admin/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/admin/form"))
                .andExpect(model().attributeExists("form", "categories", "generalTopics"));

        // 2. Gửi form tạo mới
        mockMvc.perform(post("/grammar/admin/create")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("code", "N5-UI-01")
                        .param("title", "Cấu trúc tạo qua giao diện 〜てください")
                        .param("category", "GRAMMAR")
                        .param("formula", "V-te + ください")
                        .param("explanation", "Dùng để yêu cầu lịch sự.\n- Thường dùng trong lớp học\n- Không dùng với cấp trên")
                        .param("notes", "Thể phủ định là ないでください")
                        .param("sortOrder", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/grammar/admin/")));

        // 3. Kiểm tra bản ghi CSDL
        GrammarTopic saved = topicRepository.findByCode("N5-UI-01").orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Cấu trúc tạo qua giao diện 〜てください");
        assertThat(saved.getCategory()).isEqualTo(GrammarCategory.GRAMMAR);
        assertThat(saved.getFormula()).isEqualTo("V-te + ください");

        // 4. Mở trang chi tiết kiểm tra render HTML gạch đầu dòng an toàn
        mockMvc.perform(get("/grammar/admin/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/admin/detail"))
                .andExpect(model().attributeExists("topic", "renderedExplanation"))
                .andExpect(content().string(containsString("<ul><li>Thường dùng trong lớp học</li><li>Không dùng với cấp trên</li></ul>")));

        // 5. Thêm câu ví dụ
        mockMvc.perform(post("/grammar/admin/" + saved.getId() + "/examples")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("japanese", "ここに座ってください。")
                        .param("reading", "ここにすわってください。")
                        .param("meaningVi", "Xin hãy ngồi ở đây."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/admin/" + saved.getId()));

        // 6. Thêm câu hỏi trắc nghiệm
        mockMvc.perform(post("/grammar/admin/" + saved.getId() + "/questions/create")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("prompt", "ここに　なまえを　（　）ください。")
                        .param("explanation", "Động từ 書く chia thể Te là 書いて.")
                        .param("correctChoiceIndex", "0")
                        .param("choices[0].text", "かいて")
                        .param("choices[1].text", "かき")
                        .param("choices[2].text", "かく")
                        .param("choices[3].text", "かいた"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/admin/" + saved.getId()));

        GrammarTopic reloaded = grammarService.getTopicWithDetails(saved.getId());
        assertThat(reloaded.getExamples()).hasSize(1);
        assertThat(reloaded.getQuestions()).hasSize(1);
        assertThat(reloaded.getQuestions().get(0).getChoices()).hasSize(4);
    }

    @Test
    @DisplayName("Tiêu chí: Tạo một chủ đề ngữ pháp bằng Excel (Import)")
    void testCreateGrammarTopicViaExcel() throws Exception {
        byte[] templateBytes = excelService.generateTemplate();
        MockMultipartFile file = new MockMultipartFile(
                "file", "GrammarImportTest.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        // 1. Gửi xem trước tệp Excel
        MvcResult previewResult = mockMvc.perform(multipart("/grammar/admin/import/preview")
                        .file(file)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/admin/import-preview"))
                .andExpect(model().attributeExists("report"))
                .andReturn();

        var report = (com.nihon.dto.grammar.GrammarImportPreviewReport) previewResult.getModelAndView().getModel().get("report");
        assertThat(report).isNotNull();
        assertThat(report.canCommit()).isTrue();
        String token = report.getToken();

        // 2. Xác nhận lưu vào CSDL
        mockMvc.perform(post("/grammar/admin/import/commit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/grammar/admin"))
                .andExpect(flash().attributeExists("successMessage"));

        // 3. Kiểm tra các bản ghi đã lưu vào CSDL
        assertThat(topicRepository.findByCode("N5-GR-01")).isPresent();
        assertThat(topicRepository.findByCode("N5-GR-02")).isPresent();
    }

    @Test
    @DisplayName("Xem trước người học: Thoát mã HTML <script> an toàn, không bị thực thi")
    void testLearnerPreviewEscapesHtmlScript() throws Exception {
        GrammarTopic dangerous = GrammarTopic.builder()
                .code("XSS-01")
                .title("Kiểm tra XSS")
                .category(GrammarCategory.GRAMMAR)
                .explanation("Đoạn văn có <script>alert('xss')</script> và <b>in đậm</b>.\n- Mục 1")
                .build();
        dangerous = topicRepository.save(dangerous);

        mockMvc.perform(get("/grammar/admin/" + dangerous.getId() + "/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/admin/preview"))
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;")))
                .andExpect(content().string(containsString("<ul><li>Mục 1</li></ul>")));
    }

    @Test
    @DisplayName("Tham số lạ không gây lỗi 500: ID âm, ID không tồn tại, category lạ")
    void testStrangeParametersDoNotThrow500() throws Exception {
        // ID không tồn tại trên trang danh sách / preview / detail
        mockMvc.perform(get("/grammar/admin/9999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/grammar/admin/-1"))
                .andExpect(status().isNotFound());

        // Category lạ trên trang danh sách được xử lý êm thấm (không 500)
        mockMvc.perform(get("/grammar/admin")
                        .param("category", "INVALID_CAT_NAME")
                        .param("keyword", "???***")
                        .param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("grammar/admin/list"));
    }

    @Test
    @DisplayName("Tải tệp mẫu và xuất Excel: Mã 200 OK và content-type hợp lệ")
    void testDownloadTemplateAndExport() throws Exception {
        mockMvc.perform(get("/grammar/admin/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("GrammarTemplate.xlsx")))
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/grammar/admin/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")))
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}

