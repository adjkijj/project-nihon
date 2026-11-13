package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.service.exam.ExamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamAdminControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-exam-12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamService examService;

    @Test
    @DisplayName("GET /exams/admin - Hiển thị danh sách đề thi và bộ lọc")
    void testGetExamList() throws Exception {
        mockMvc.perform(get("/exams/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/list"))
                .andExpect(model().attributeExists("examsPage", "categories"))
                .andExpect(content().string(containsString("Quản lý Đề thi")));
    }

    @Test
    @DisplayName("GET /exams/admin/new - Hiển thị form tạo mới đề thi")
    void testGetNewExamForm() throws Exception {
        mockMvc.perform(get("/exams/admin/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/paper-form"))
                .andExpect(model().attributeExists("form", "categories", "topics"))
                .andExpect(content().string(containsString("Tạo Đề thi Mới")));
    }

    @Test
    @DisplayName("POST /exams/admin/new - Tạo đề thi hợp lệ và chuyển hướng đến trang chi tiết")
    void testPostNewExamSuccess() throws Exception {
        mockMvc.perform(post("/exams/admin/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("code", "CTRL-TEST-01")
                        .param("title", "Đề thi kiểm tra Controller")
                        .param("category", "MIDTERM")
                        .param("durationMinutes", "45")
                        .param("published", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/exams/admin/")));
    }

    @Test
    @DisplayName("POST /exams/admin/new - Dữ liệu lỗi (mã đề trống) render lại form báo lỗi")
    void testPostNewExamValidationError() throws Exception {
        mockMvc.perform(post("/exams/admin/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("code", "")
                        .param("title", "")
                        .param("durationMinutes", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/paper-form"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("GET /exams/admin/{id} - Hiển thị trang chi tiết đề thi và cấu trúc phần thi")
    void testGetExamDetail() throws Exception {
        ExamPaper paper = examService.createExam(com.nihon.dto.exam.ExamPaperForm.builder()
                .code("CTRL-DETAIL-01")
                .title("Đề xem chi tiết")
                .category(ExamCategory.MOCK.name())
                .durationMinutes(60)
                .build());

        mockMvc.perform(get("/exams/admin/" + paper.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/detail"))
                .andExpect(model().attributeExists("paper", "newSectionForm"))
                .andExpect(content().string(containsString("CTRL-DETAIL-01")));
    }

    @Test
    @DisplayName("GET /exams/admin/{id}/preview - Hiển thị màn hình xem trước toàn bộ đề thi")
    void testGetExamPreview() throws Exception {
        ExamPaper paper = examService.createExam(com.nihon.dto.exam.ExamPaperForm.builder()
                .code("CTRL-PREV-01")
                .title("Đề xem trước")
                .category(ExamCategory.REVIEW.name())
                .durationMinutes(30)
                .build());

        mockMvc.perform(get("/exams/admin/" + paper.getId() + "/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/preview"))
                .andExpect(model().attributeExists("paper"))
                .andExpect(content().string(containsString("Xem trước Đề thi")));
    }

    @Test
    @DisplayName("GET /exams/admin/import - Hiển thị trang tải lên file Excel")
    void testGetImportPage() throws Exception {
        mockMvc.perform(get("/exams/admin/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/import"))
                .andExpect(content().string(containsString("Nhập Đề thi từ Tệp Excel")));
    }

    @Test
    @DisplayName("GET /exams/admin/template - Tải xuống tệp Excel mẫu ExamTemplate.xlsx")
    void testDownloadTemplate() throws Exception {
        mockMvc.perform(get("/exams/admin/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("ExamTemplate.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("GET /exams/admin/{id}/export - Xuất tệp Excel cho đề thi có sẵn")
    void testExportExam() throws Exception {
        ExamPaper paper = examService.createExam(com.nihon.dto.exam.ExamPaperForm.builder()
                .code("CTRL-EXP-01")
                .title("Đề xuất file")
                .category(ExamCategory.QUICK_TEST.name())
                .durationMinutes(15)
                .build());

        mockMvc.perform(get("/exams/admin/" + paper.getId() + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("nihon-exam-CTRL-EXP-01")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}

