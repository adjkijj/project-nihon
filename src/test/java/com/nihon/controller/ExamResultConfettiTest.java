package com.nihon.controller;

import com.nihon.dto.exam.taker.ExamAttemptStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamResultConfettiTest {

    private static final Path SCRIPT_PATH = Paths.get("src", "main", "resources", "static", "js", "confetti.js");
    private static final Path TEMPLATE_PATH = Paths.get("src", "main", "resources", "templates", "exams", "taker", "result.html");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Test
    @DisplayName("1. confetti.js tồn tại và đáp ứng đầy đủ yêu cầu: Canvas 2D, không dùng thư viện ngoài, ngưỡng 90%, prefers-reduced-motion, dừng sau 3 giây, aria-hidden, pointer-events none")
    void testConfettiScriptRequirements() throws IOException {
        assertThat(Files.exists(SCRIPT_PATH))
                .as("File script static/js/confetti.js phải tồn tại")
                .isTrue();

        String js = Files.readString(SCRIPT_PATH, StandardCharsets.UTF_8);

        // 1. Dùng Canvas 2D, không dùng thư viện ngoài
        assertThat(js).contains("getContext('2d')");
        assertThat(js).contains("requestAnimationFrame");
        assertThat(js).doesNotContain("npm");
        assertThat(js).doesNotContain("cdn.");
        assertThat(js).doesNotContain("require(");
        assertThat(js).doesNotContain("import ");

        // 2. Chỉ chạy khi điểm phần trăm từ 90 trở lên
        assertThat(js).contains("90");
        assertThat(js).contains("data-percentage");

        // 3. Tôn trọng prefers-reduced-motion: không chạy hiệu ứng
        assertThat(js).contains("prefers-reduced-motion");
        assertThat(js).contains("matchMedia");

        // 4. Dừng sau 3 giây và xóa canvas
        assertThat(js).contains("3000");
        assertThat(js).contains("removeChild");

        // 5. Canvas có aria-hidden=true và pointer-events none
        assertThat(js).contains("aria-hidden");
        assertThat(js).contains("true");
        assertThat(js).contains("pointer-events");
        assertThat(js).contains("pointerEvents");
        assertThat(js).contains("none");
    }

    @Test
    @DisplayName("2. Template exams/taker/result.html tải confetti.js, gắn data-percentage và tuân thủ CSP không dùng script inline")
    void testExamResultTemplateConfettiIntegration() throws IOException {
        assertThat(Files.exists(TEMPLATE_PATH))
                .as("Template exams/taker/result.html phải tồn tại")
                .isTrue();

        String html = Files.readString(TEMPLATE_PATH, StandardCharsets.UTF_8);

        // Nạp script confetti.js
        assertThat(html).contains("confetti.js");

        // Gắn thuộc tính data-percentage
        assertThat(html).contains("data-percentage");

        // Tuân thủ CSP: Không chứa script inline và không có onclick
        String stripped = html.replaceAll("(?s)<!--.*?-->", "");
        assertThat(stripped).doesNotMatch("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*");
        assertThat(stripped).doesNotContain("onclick=");
    }

    @Test
    @DisplayName("3. Kết quả bài thi đạt >= 90% (Hạng S) render thẻ data-percentage tương ứng và nạp confetti.js")
    void testExamResultEndpointHighScoreRendersConfettiAsset() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        ExamPaper paper = createTestPaper("CONFETTI-HIGH-" + System.currentTimeMillis());
        // Điểm 18/20 = 90% -> Hạng S
        ExamAttempt attempt = createTestAttempt(paper, learnerId, new BigDecimal("18.00"), new BigDecimal("20.00"));

        mockMvc.perform(get("/exams/attempt/" + attempt.getId() + "/result")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("confetti.js")))
                .andExpect(content().string(containsString("data-percentage=\"90.0")))
                .andExpect(content().string(containsString("Hạng S")));
    }

    @Test
    @DisplayName("4. Kết quả bài thi dưới 90% (ví dụ 70%) vẫn render đầy đủ nhưng data-percentage < 90")
    void testExamResultEndpointLowerScoreDataPercentage() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        ExamPaper paper = createTestPaper("CONFETTI-LOW-" + System.currentTimeMillis());
        // Điểm 14/20 = 70% -> Hạng B
        ExamAttempt attempt = createTestAttempt(paper, learnerId, new BigDecimal("14.00"), new BigDecimal("20.00"));

        mockMvc.perform(get("/exams/attempt/" + attempt.getId() + "/result")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("confetti.js")))
                .andExpect(content().string(containsString("data-percentage=\"70.0")))
                .andExpect(content().string(containsString("Hạng B")));
    }

    private ExamPaper createTestPaper(String code) {
        ExamPaper paper = ExamPaper.builder()
                .code(code)
                .title("Đề kiểm tra hiệu ứng Confetti")
                .category(ExamCategory.MOCK)
                .published(true)
                .durationMinutes(20)
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
                .promptJa("テスト問題1")
                .sortOrder(1)
                .points(BigDecimal.TEN)
                .choices(new ArrayList<>())
                .build();
        q1.addChoice(ExamChoice.builder().sortOrder(1).text("A").correct(true).build());
        section.addQuestion(q1);

        ExamQuestion q2 = ExamQuestion.builder()
                .section(section)
                .type(ExamQuestionType.MULTIPLE_CHOICE)
                .promptJa("テスト問題2")
                .sortOrder(2)
                .points(BigDecimal.TEN)
                .choices(new ArrayList<>())
                .build();
        q2.addChoice(ExamChoice.builder().sortOrder(1).text("B").correct(true).build());
        section.addQuestion(q2);

        return paperRepository.save(paper);
    }

    private ExamAttempt createTestAttempt(ExamPaper paper, String learnerId, BigDecimal totalPoints, BigDecimal maxPoints) {
        ExamAttempt attempt = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(paper)
                .learnerId(learnerId)
                .status(ExamAttemptStatus.FINISHED)
                .startedAt(Instant.now().minusSeconds(300))
                .submittedAt(Instant.now())
                .durationSeconds(300)
                .totalPoints(totalPoints)
                .maxPoints(maxPoints)
                .answers(new ArrayList<>())
                .build();
        return attemptRepository.save(attempt);
    }
}

