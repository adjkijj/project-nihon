package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamTakerWorkflowIntegrationTest {

    private static final String CSRF_TOKEN = "workflow-csrf-token-taking";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    private ExamPaper threeSectionPaper;
    private Long q1Id;
    private Long q2Id;
    private Long q3Id;
    private Long choice1Id;

    @BeforeEach
    void setUp() {
        paperRepository.deleteAll();

        threeSectionPaper = ExamPaper.builder()
                .code("JLPT-N5-3SECTIONS")
                .title("Đề thi JLPT N5 Ba Phần Chuẩn")
                .description("Đề thi thử đầy đủ 3 phần thi theo chuẩn JLPT")
                .durationMinutes(60)
                .category(ExamCategory.MOCK)
                .published(true)
                .shuffleChoices(false)
                .sections(new ArrayList<>())
                .build();

        // Phần 1: Kiến thức ngôn ngữ (Từ vựng - Trắc nghiệm)
        ExamSection sec1 = ExamSection.builder()
                .title("Phần 1: Kiến thức ngôn ngữ (Từ vựng)")
                .sortOrder(1)
                .instructions("Chọn từ thích hợp để điền vào chỗ trống")
                .questions(new ArrayList<>())
                .build();

        ExamQuestion q1 = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("あした、わたしは　東京へ　（　　）。")
                .promptVi("Ngày mai tôi đi Tokyo.")
                .points(BigDecimal.valueOf(1.00))
                .choices(new ArrayList<>())
                .build();

        ExamChoice c1 = ExamChoice.builder().sortOrder(1).text("いきます").correct(true).build();
        ExamChoice c2 = ExamChoice.builder().sortOrder(2).text("いきました").correct(false).build();
        q1.addChoice(c1);
        q1.addChoice(c2);
        sec1.addQuestion(q1);

        // Phần 2: Ngữ pháp (Ghép câu sao JLPT)
        ExamSection sec2 = ExamSection.builder()
                .title("Phần 2: Ngữ pháp (Ghép câu sao)")
                .sortOrder(2)
                .instructions("Sắp xếp các từ để hoàn chỉnh câu")
                .questions(new ArrayList<>())
                .build();

        ExamQuestion q2 = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.ORDERING)
                .promptJa("わたしは　＿　＿　★　＿　いきます。")
                .promptVi("Tôi đi đến trường bằng xe đạp.")
                .points(BigDecimal.valueOf(2.00))
                .choices(new ArrayList<>())
                .build();

        q2.addChoice(ExamChoice.builder().sortOrder(1).text("がっこうへ").build());
        q2.addChoice(ExamChoice.builder().sortOrder(2).text("じてんしゃで").build());
        q2.addChoice(ExamChoice.builder().sortOrder(3).text("まいあさ").build());
        q2.addChoice(ExamChoice.builder().sortOrder(4).text("ひとりで").build());
        sec2.addQuestion(q2);

        // Phần 3: Viết & Dịch
        ExamSection sec3 = ExamSection.builder()
                .title("Phần 3: Dịch thuật & Diễn đạt")
                .sortOrder(3)
                .instructions("Dịch câu sau sang tiếng Nhật")
                .questions(new ArrayList<>())
                .build();

        ExamQuestion q3 = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.TRANSLATION)
                .promptVi("Xin chào, tôi là học sinh.")
                .promptJa("こんにちは、わたしはがくせいです。")
                .points(BigDecimal.valueOf(2.00))
                .build();
        sec3.addQuestion(q3);

        threeSectionPaper.addSection(sec1);
        threeSectionPaper.addSection(sec2);
        threeSectionPaper.addSection(sec3);

        threeSectionPaper = paperRepository.save(threeSectionPaper);

        q1Id = q1.getId();
        choice1Id = c1.getId();
        q2Id = q2.getId();
        q3Id = q3.getId();
    }

    @Test
    @DisplayName("Tiêu chí: Làm đề ba phần, đóng tab giữa chừng rồi mở lại vẫn còn câu trả lời")
    void testCompleteThreeSectionExamWithMidwayTabCloseAndRestore() throws Exception {
        MockHttpSession learnerSession = new MockHttpSession();

        // Bước 1: Thí sinh bấm bắt đầu bài thi (POST /exams/{code}/start)
        MvcResult startResult = mockMvc.perform(post("/exams/" + threeSectionPaper.getCode() + "/start")
                        .session(learnerSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/exams/attempt/")))
                .andReturn();

        String location = startResult.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        String attemptId = location.substring(location.lastIndexOf('/') + 1);

        // Bước 2: Trả lời Phần 1 (MULTIPLE_CHOICE)
        mockMvc.perform(post("/exams/attempt/" + attemptId + "/answer")
                        .session(learnerSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(q1Id))
                        .param("answer", String.valueOf(choice1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.answeredCount").value(1));

        // Bước 3: Trả lời Phần 2 (ORDERING: chuỗi thứ tự ghép "3,2,4,1")
        mockMvc.perform(post("/exams/attempt/" + attemptId + "/answer")
                        .session(learnerSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(q2Id))
                        .param("answer", "3,2,4,1")
                        .param("markedForReview", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.answeredCount").value(2));

        // =====================================================================
        // Bước 4: MÔ PHỎNG ĐÓNG TAB GIỮA CHỪNG RỒI MỞ LẠI PHÒNG THI
        // Request GET mới trên cùng session tới /exams/attempt/{attemptId}
        // =====================================================================
        MvcResult reopenResult = mockMvc.perform(get("/exams/attempt/" + attemptId)
                        .session(learnerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/room"))
                .andExpect(model().attributeExists("attempt", "paper", "remainingSeconds"))
                .andReturn();

        String html = reopenResult.getResponse().getContentAsString();

        // 4.1: Kiểm tra Phần 1: lựa chọn đã chọn trước đó vẫn được khôi phục checked
        assertThat(html).contains("value=\"" + choice1Id + "\"");
        assertThat(html).contains("checked");

        // 4.2: Kiểm tra Phần 2: giá trị thứ tự ghép "3,2,4,1" vẫn còn nguyên trong input
        assertThat(html).contains("value=\"3,2,4,1\"");

        // 4.3: Kiểm tra Bảng Palette: số câu đã làm vẫn hiển thị chính xác là 2
        assertThat(html).contains("paletteAnsweredCount\">2<");

        // Bước 5: Tiếp tục trả lời Phần 3 (TRANSLATION)
        String translationText = "こんにちは、わたしはがくせいです。";
        mockMvc.perform(post("/exams/attempt/" + attemptId + "/answer")
                        .session(learnerSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("questionId", String.valueOf(q3Id))
                        .param("answer", translationText))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.answeredCount").value(3));

        // Bước 6: Thí sinh nộp bài thi (POST /exams/attempt/{attemptId}/finish)
        mockMvc.perform(post("/exams/attempt/" + attemptId + "/finish")
                        .session(learnerSession)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attemptId + "/result"));

        // Bước 7: Màn hình kết quả tóm tắt (GET /exams/attempt/{attemptId}/result)
        mockMvc.perform(get("/exams/attempt/" + attemptId + "/result")
                        .session(learnerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("exams/taker/result"))
                .andExpect(model().attributeExists("attempt", "paper"))
                .andExpect(content().string(containsString("ĐÃ NỘP BÀI THÀNH CÔNG")))
                .andExpect(content().string(containsString("3"))) // Tổng số câu và số câu đã làm
                .andExpect(content().string(containsString("JLPT-N5-3SECTIONS")));

        // Bước 8: Chặn quay lại sửa sau khi nộp (Mở lại phòng thi bị chuyển hướng về kết quả)
        mockMvc.perform(get("/exams/attempt/" + attemptId)
                        .session(learnerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/exams/attempt/" + attemptId + "/result"));
    }
}
