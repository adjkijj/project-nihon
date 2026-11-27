package com.nihon.service.exam.grading;

import com.nihon.dto.exam.result.ExamResultViewDto;
import com.nihon.dto.exam.taker.ExamAttemptStatus;
import com.nihon.entity.ExamAnswerKey;
import com.nihon.entity.ExamAnswerKind;
import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamChoice;
import com.nihon.entity.ExamPaper;
import com.nihon.entity.ExamQuestion;
import com.nihon.entity.ExamQuestionType;
import com.nihon.entity.ExamSection;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.ExamAttemptRepository;
import com.nihon.repository.ExamPaperRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử phương thức ExamGradingService.calculateGrade(BigDecimal percentage)
 * và việc hiển thị huy hiệu xếp hạng trên exams/taker/result.html:
 * - Ngưỡng từ application.properties: app.exam.grade.s=90, app.exam.grade.a=80, app.exam.grade.b=65.
 * - Test đầy đủ các giá trị biên: 64.99, 65, 79.99, 80, 89.99, 90, 100, 0.
 * - Huy hiệu được tính lúc render DTO/Thymeleaf, không thêm cột CSDL.
 * - Không ghi là chuẩn JLPT (đây là ngưỡng riêng của Project Nihon).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamGradingGradeTest {

    @Autowired
    private ExamGradingService gradingService;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "Tỷ lệ {0}% xếp hạng {1}")
    @CsvSource({
            "64.99, C",
            "65, B",
            "79.99, B",
            "80, A",
            "89.99, A",
            "90, S",
            "100, S",
            "0, C"
    })
    @DisplayName("1. Kiểm thử các giá trị biên theo yêu cầu prompt: 64.99, 65, 79.99, 80, 89.99, 90, 100, 0")
    void testCalculateGrade_promptBoundaryValues(String percentageStr, String expectedGrade) {
        BigDecimal percentage = new BigDecimal(percentageStr);
        String grade = gradingService.calculateGrade(percentage);
        assertThat(grade)
                .withFailMessage("Tỷ lệ %s%% phải xếp hạng %s nhưng nhận được %s", percentageStr, expectedGrade, grade)
                .isEqualTo(expectedGrade);
    }

    @Test
    @DisplayName("2. Kiểm thử các giá trị đặc biệt: null, số âm, lớn hơn 100")
    void testCalculateGrade_specialValues() {
        assertThat(gradingService.calculateGrade((BigDecimal) null)).isEqualTo("C");
        assertThat(gradingService.calculateGrade(new BigDecimal("-10"))).isEqualTo("C");
        assertThat(gradingService.calculateGrade(new BigDecimal("105"))).isEqualTo("S");
        assertThat(gradingService.calculateGrade(new BigDecimal("65.000"))).isEqualTo("B");
        assertThat(gradingService.calculateGrade(new BigDecimal("80.000"))).isEqualTo("A");
        assertThat(gradingService.calculateGrade(new BigDecimal("90.000"))).isEqualTo("S");
    }

    @Test
    @DisplayName("3. Hỗ trợ nạp ngưỡng tùy chỉnh linh hoạt")
    void testCalculateGrade_customThresholds() {
        ExamGradingService customService = new ExamGradingService(null, null, null, null, List.of());
        customService.setGradeThresholds(new BigDecimal("95"), new BigDecimal("85"), new BigDecimal("70"));

        assertThat(customService.calculateGrade(new BigDecimal("94.99"))).isEqualTo("A");
        assertThat(customService.calculateGrade(new BigDecimal("95.00"))).isEqualTo("S");
        assertThat(customService.calculateGrade(new BigDecimal("84.99"))).isEqualTo("B");
        assertThat(customService.calculateGrade(new BigDecimal("85.00"))).isEqualTo("A");
        assertThat(customService.calculateGrade(new BigDecimal("69.99"))).isEqualTo("C");
        assertThat(customService.calculateGrade(new BigDecimal("70.00"))).isEqualTo("B");
    }

    @Test
    @DisplayName("4. Kết quả bài thi trên exams/taker/result.html hiển thị huy hiệu xếp hạng, tính lúc render, không ghi chuẩn JLPT")
    void testResultViewRendersGradeBadgeWithoutJlptLabel() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        ExamPaper paper = ExamPaper.builder()
                .code("GRADE-TEST-" + System.currentTimeMillis())
                .title("Đề kiểm tra xếp hạng bài thi")
                .category(ExamCategory.MOCK)
                .published(true)
                .durationMinutes(15)
                .sections(new ArrayList<>())
                .build();
        paper = paperRepository.save(paper);

        ExamSection section = ExamSection.builder()
                .paper(paper)
                .title("Phần 1: Trắc nghiệm")
                .sortOrder(1)
                .questions(new ArrayList<>())
                .build();
        paper.getSections().add(section);

        ExamQuestion q1 = ExamQuestion.builder()
                .section(section)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("Chọn từ đúng")
                .sortOrder(1)
                .points(BigDecimal.TEN)
                .choices(new ArrayList<>())
                .build();
        q1.addChoice(ExamChoice.builder().sortOrder(1).text("A").correct(true).build());
        section.addQuestion(q1);

        ExamQuestion q2 = ExamQuestion.builder()
                .section(section)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("Chọn cách đọc")
                .sortOrder(2)
                .points(BigDecimal.TEN)
                .choices(new ArrayList<>())
                .build();
        q2.addChoice(ExamChoice.builder().sortOrder(1).text("B").correct(true).build());
        section.addQuestion(q2);

        paper = paperRepository.save(paper);

        // Tạo bài thi đạt 18/20 điểm = 90% -> Hạng S
        String attemptId = UUID.randomUUID().toString();
        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId)
                .paper(paper)
                .learnerId(learnerId)
                .status(ExamAttemptStatus.FINISHED)
                .startedAt(Instant.now().minusSeconds(300))
                .submittedAt(Instant.now())
                .durationSeconds(300)
                .totalPoints(new BigDecimal("18.00"))
                .maxPoints(new BigDecimal("20.00"))
                .answers(new ArrayList<>())
                .build();
        attempt = attemptRepository.save(attempt);

        // Gọi trang kết quả với cookie của đúng learnerId
        mockMvc.perform(get("/exams/attempt/" + attempt.getId() + "/result")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("result"))
                .andExpect(model().attribute("grade", "S"))
                // Kiểm tra huy hiệu hạng S trên template HTML
                .andExpect(content().string(containsString("exam-grade-badge")))
                .andExpect(content().string(containsString("Hạng S")))
                .andExpect(content().string(containsString("grade-s")))
                // Không được ghi là chuẩn JLPT cho bảng xếp hạng này
                .andExpect(content().string(not(containsString("chuẩn JLPT cho xếp hạng"))));
    }

    @Test
    @DisplayName("5. Template exams/taker/result.html tuân thủ CSP không chứa inline script hoặc onclick")
    void testResultTemplateCspCompliance() throws IOException {
        Path templatePath = Paths.get("src/main/resources/templates/exams/taker/result.html");
        String content = Files.readString(templatePath, StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");

        assertThat(content).doesNotMatch("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*");
        assertThat(content).doesNotContain("onclick=");
    }
}
