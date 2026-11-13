package com.nihon.controller;

import com.nihon.dto.exam.taker.ExamSessionAttempt;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.repository.ExamPaperRepository;
import com.nihon.service.exam.ExamTakingService;
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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamHtmlAnswerLeakTest {

    private static final String SECRET_EXPLANATION_MCQ = "BI_MAT_GIAI_THICH_TRAC_NGHIEM_CHUYEN_SAU_999";
    private static final String SECRET_EXPLANATION_TYPING = "BI_MAT_GIAI_THICH_GO_KANA_123";
    private static final String SECRET_ANSWER_KEY_ACCEPTED = "DAP_AN_ACCEPT_CHUAN_KANA_777";
    private static final String SECRET_ANSWER_KEY_ORDER = "ORDER_KEY_4321_SECRET";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamTakingService takingService;

    private ExamPaper secretPaper;

    @BeforeEach
    void setUp() {
        secretPaper = ExamPaper.builder()
                .code("LEAK-TEST-01")
                .title("Đề thi kiểm tra an toàn dữ liệu đáp án")
                .durationMinutes(30)
                .category(ExamCategory.MOCK)
                .published(true)
                .sections(new ArrayList<>())
                .build();

        ExamSection sec = ExamSection.builder()
                .title("Phần bảo mật")
                .sortOrder(1)
                .questions(new ArrayList<>())
                .build();

        // 1. Câu trắc nghiệm có đáp án đúng và lời giải
        ExamQuestion qMcq = ExamQuestion.builder()
                .sortOrder(1)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("ほんを　よみます。")
                .points(BigDecimal.valueOf(1.00))
                .explanation(SECRET_EXPLANATION_MCQ)
                .choices(new ArrayList<>())
                .build();

        qMcq.addChoice(ExamChoice.builder().sortOrder(1).text("Lựa chọn 1").correct(true).build());
        qMcq.addChoice(ExamChoice.builder().sortOrder(2).text("Lựa chọn 2").correct(false).build());

        // 2. Câu gõ có Answer Key và lời giải
        ExamQuestion qTyping = ExamQuestion.builder()
                .sortOrder(2)
                .type(ExamQuestionType.TYPING)
                .promptJa("にほんご")
                .points(BigDecimal.valueOf(1.00))
                .explanation(SECRET_EXPLANATION_TYPING)
                .answerKeys(new ArrayList<>())
                .build();

        qTyping.addAnswerKey(ExamAnswerKey.builder()
                .answerText(SECRET_ANSWER_KEY_ACCEPTED)
                .kind(ExamAnswerKind.ACCEPTED)
                .build());

        // 3. Câu ghép có Order Key
        ExamQuestion qOrder = ExamQuestion.builder()
                .sortOrder(3)
                .type(ExamQuestionType.ORDERING)
                .promptJa("わたしは ＿ ＿ ★ ＿ いきます。")
                .points(BigDecimal.valueOf(2.00))
                .answerKeys(new ArrayList<>())
                .choices(new ArrayList<>())
                .build();

        qOrder.addChoice(ExamChoice.builder().sortOrder(1).text("mảnh 1").build());
        qOrder.addChoice(ExamChoice.builder().sortOrder(2).text("mảnh 2").build());
        qOrder.addAnswerKey(ExamAnswerKey.builder()
                .answerText(SECRET_ANSWER_KEY_ORDER)
                .kind(ExamAnswerKind.ORDER)
                .build());

        sec.addQuestion(qMcq);
        sec.addQuestion(qTyping);
        sec.addQuestion(qOrder);
        secretPaper.addSection(sec);
        paperRepository.save(secretPaper);
    }

    @Test
    @DisplayName("Quét toàn bộ HTML phòng thi: TUYỆT ĐỐI KHÔNG chứa cờ correct, lời giải explanation hay nội dung answer_key")
    void testHtmlDoesNotLeakAnswersOrExplanations() throws Exception {
        MockHttpSession session = new MockHttpSession();
        ExamSessionAttempt attempt = takingService.startExam(secretPaper.getCode(), session);

        MvcResult result = mockMvc.perform(get("/exams/attempt/" + attempt.getAttemptId()).session(session))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();

        // 1. Không chứa chuỗi explanation bí mật
        assertThat(html).doesNotContain(SECRET_EXPLANATION_MCQ);
        assertThat(html).doesNotContain(SECRET_EXPLANATION_TYPING);

        // 2. Không chứa các khóa đáp án (answer key) của giáo viên
        assertThat(html).doesNotContain(SECRET_ANSWER_KEY_ACCEPTED);
        assertThat(html).doesNotContain(SECRET_ANSWER_KEY_ORDER);

        // 3. Không chứa cờ đáp án đúng (correct flag) trong bất kỳ thẻ hay thuộc tính HTML nào
        Pattern correctAttrPattern = Pattern.compile("(?i)(data-correct|is-correct|correct\\s*=\\s*\"true\"|class=\"[^\"]*correct[^\"]*\")");
        assertThat(correctAttrPattern.matcher(html).find())
                .withFailMessage("Phát hiện cờ correct trong mã HTML trả về cho thí sinh!")
                .isFalse();
    }
}
