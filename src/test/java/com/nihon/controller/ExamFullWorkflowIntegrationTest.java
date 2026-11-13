package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.exam.ExamImportPreviewReport;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.service.exam.ExamExcelService;
import com.nihon.service.exam.ExamService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tích hợp toàn trình (End-to-End Workflow Test):
 * 1. Tạo một đề thi hoàn chỉnh thông qua giao diện web (UI Controller).
 * 2. Nhập một đề thi hoàn chỉnh thông qua tệp Excel hai bước (Excel Import Preview & Commit).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamFullWorkflowIntegrationTest {

    private static final String CSRF_TOKEN = "test-workflow-csrf-token";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamExcelService examExcelService;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Test
    @DisplayName("Tiêu chí 1: Tạo đề thi mẫu đầy đủ qua giao diện Web Controller và xuất bản")
    void testCreateCompleteExamViaWebUI() throws Exception {
        // Bước 1: Tạo đề thi mới (ExamPaper)
        MvcResult createPaperResult = mockMvc.perform(post("/exams/admin/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("code", "UI-MOCK-N5-01")
                        .param("title", "Đề thi thử N5 tạo qua Giao diện")
                        .param("category", "MOCK")
                        .param("durationMinutes", "60")
                        .param("description", "Đề thi thực hành tạo qua giao diện web")
                        .param("published", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/exams/admin/")))
                .andReturn();

        String redirectUrl = createPaperResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        Long paperId = Long.parseLong(redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1));

        // Bước 2: Thêm phần thi mới (ExamSection)
        mockMvc.perform(post("/exams/admin/" + paperId + "/sections")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("paperId", String.valueOf(paperId))
                        .param("title", "Phần 1: Kiến thức ngôn ngữ (Từ vựng & Kanji)")
                        .param("instructions", "Chọn đáp án đúng nhất cho mỗi câu hỏi."))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/admin/" + paperId));

        entityManager.flush();
        entityManager.clear();

        ExamPaper paper = examService.getExamWithDetails(paperId);
        assertThat(paper.getSections()).hasSize(1);
        ExamSection section = paper.getSections().get(0);

        // Bước 3: Thêm câu hỏi trắc nghiệm (MULTIPLE_CHOICE)
        mockMvc.perform(post("/exams/admin/sections/" + section.getId() + "/questions/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("paperId", String.valueOf(paperId))
                        .param("type", "MULTIPLE_CHOICE")
                        .param("points", "1.00")
                        .param("promptJa", "あした、わたしは　東京へ　（　　）。")
                        .param("promptVi", "Ngày mai tôi (   ) Tokyo.")
                        .param("choice1", "いきます")
                        .param("choice2", "いきました")
                        .param("choice3", "いきません")
                        .param("choice4", "いかない")
                        .param("correctChoiceIndex", "1")
                        .param("explanation", "Vì có 'あした' (ngày mai) nên dùng thể tương lai lịch sự 'いきます'.")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/admin/" + paperId));

        // Bước 4: Thêm câu hỏi sắp xếp vị trí sao JLPT (ORDERING)
        mockMvc.perform(post("/exams/admin/sections/" + section.getId() + "/questions/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("paperId", String.valueOf(paperId))
                        .param("type", "ORDERING")
                        .param("points", "2.00")
                        .param("promptJa", "わたしは　＿　＿　★　＿　いきます。")
                        .param("promptVi", "Tôi đi đến trường bằng xe đạp.")
                        .param("choice1", "がっこうへ")
                        .param("choice2", "じてんしゃで")
                        .param("choice3", "まいあさ")
                        .param("choice4", "ひとりで")
                        .param("orderingOrder", "3,2,4,1")
                        .param("orderingStar", "3")
                        .param("explanation", "Thứ tự: まいあさ(3) じてんしゃで(2) ひとりで(4) がっこうへ(1) いきます.")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/admin/" + paperId));

        entityManager.flush();
        entityManager.clear();

        // Bước 5: Xem trước đề thi hoàn chỉnh (GET /exams/admin/{id}/preview)
        mockMvc.perform(get("/exams/admin/" + paperId + "/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/preview"))
                .andExpect(model().attributeExists("paper"))
                .andExpect(content().string(containsString("UI-MOCK-N5-01")))
                .andExpect(content().string(containsString("Đề thi thử N5 tạo qua Giao diện")))
                .andExpect(content().string(containsString("あした、わたしは")))
                .andExpect(content().string(containsString("東京へ")))
                .andExpect(content().string(containsString("★")));

        // Bước 6: Xuất bản đề thi (POST /exams/admin/{id}/publish)
        mockMvc.perform(post("/exams/admin/" + paperId + "/publish")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/admin/" + paperId));

        ExamPaper publishedPaper = examService.getExamById(paperId);
        assertThat(publishedPaper.isPublished()).isTrue();
        assertThat(publishedPaper.getTotalQuestions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tiêu chí 2: Nhập đề thi mẫu hoàn chỉnh từ Excel hai bước (Preview & Commit)")
    void testImportCompleteExamViaExcelTwoSteps() throws Exception {
        // Tải tệp mẫu chuẩn của Project Nihon
        byte[] templateBytes = examExcelService.generateTemplate();
        MockMultipartFile excelFile = new MockMultipartFile(
                "file",
                "ExamTemplate.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                templateBytes
        );

        // Bước 1: Xem trước và thẩm định (POST /exams/admin/import/preview)
        MvcResult previewResult = mockMvc.perform(multipart("/exams/admin/import/preview")
                        .file(excelFile)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/admin/import-preview"))
                .andExpect(model().attributeExists("report"))
                .andExpect(content().string(containsString("Bước 2: Kết Quả Xem Trước")))
                .andExpect(content().string(containsString("N5-EXAM-01")))
                .andReturn();

        ExamImportPreviewReport report = (ExamImportPreviewReport) previewResult.getModelAndView().getModel().get("report");
        assertThat(report).isNotNull();
        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getToken()).isNotBlank();

        // Bước 2: Xác nhận nhập dữ liệu vào cơ sở dữ liệu (POST /exams/admin/import/commit)
        mockMvc.perform(post("/exams/admin/import/commit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("token", report.getToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/exams/admin/")));

        entityManager.flush();
        entityManager.clear();

        // Kiểm tra dữ liệu được lưu trong CSDL
        ExamPaper paper = paperRepository.findByCode("N5-EXAM-01").orElseThrow();
        assertThat(paper.getTitle()).isEqualTo("Đề thi thử JLPT N5 - Đề số 1");
        assertThat(paper.getDurationMinutes()).isEqualTo(50);
        assertThat(paper.getCategory().name()).isEqualTo("MOCK");

        ExamPaper paperWithDetails = examService.getExamWithDetails(paper.getId());
        assertThat(paperWithDetails.getSections()).hasSize(2);
        assertThat(paperWithDetails.getTotalQuestions()).isEqualTo(3);

        // Kiểm tra xuất lại ra Excel (GET /exams/admin/{id}/export)
        mockMvc.perform(get("/exams/admin/" + paper.getId() + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("nihon-exam-N5-EXAM-01")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}

