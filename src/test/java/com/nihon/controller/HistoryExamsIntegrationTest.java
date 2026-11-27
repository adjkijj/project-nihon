package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.entity.ExamAttempt;
import com.nihon.entity.ExamCategory;
import com.nihon.entity.ExamPaper;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.repository.ExamAttemptRepository;
import com.nihon.repository.ExamPaperRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryExamsIntegrationTest {

    private static final String CSRF_TOKEN = "csrf-history-exams";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamPaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    private ExamPaper paper;

    @BeforeEach
    void setUp() {
        paper = paperRepository.save(ExamPaper.builder()
                .code("HIST-TEST-" + System.currentTimeMillis())
                .title("Đề thi Lịch sử Test")
                .durationMinutes(20)
                .category(ExamCategory.REVIEW)
                .published(true)
                .build());
    }

    @Test
    @DisplayName("GET /history: Hiển thị danh sách bài thi đã nộp theo learnerId")
    void testViewExamAttemptsInHistory() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        ExamAttempt a1 = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(paper)
                .learnerId(learnerId)
                .startedAt(Instant.now().minusSeconds(1200))
                .submittedAt(Instant.now())
                .durationSeconds(1200)
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        attemptRepository.save(a1);

        mockMvc.perform(get("/history").cookie(learnerCookie))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("examAttempts"))
                .andExpect(content().string(containsString("Bài thi đã làm")))
                .andExpect(content().string(containsString("Đề thi Lịch sử Test")));
    }

    @Test
    @DisplayName("POST /history/exams/{id}/delete: Xóa một bài thi theo learnerId")
    void testDeleteSingleExamAttempt() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        ExamAttempt a1 = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(paper)
                .learnerId(learnerId)
                .startedAt(Instant.now().minusSeconds(600))
                .submittedAt(Instant.now())
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        attemptRepository.save(a1);

        mockMvc.perform(post("/history/exams/" + a1.getId() + "/delete")
                        .cookie(learnerCookie)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/history"))
                .andExpect(flash().attributeExists("historyMessage"));

        assertThat(attemptRepository.findById(a1.getId())).isEmpty();
    }

    @Test
    @DisplayName("POST /history/exams/delete-all: Xóa toàn bộ bài thi của learnerId")
    void testDeleteAllExamAttempts() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        Cookie learnerCookie = new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId);

        ExamAttempt a1 = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(paper)
                .learnerId(learnerId)
                .startedAt(Instant.now().minusSeconds(600))
                .submittedAt(Instant.now())
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        ExamAttempt a2 = ExamAttempt.builder()
                .id(UUID.randomUUID().toString())
                .paper(paper)
                .learnerId(learnerId)
                .startedAt(Instant.now().minusSeconds(300))
                .submittedAt(Instant.now())
                .status(com.nihon.dto.exam.taker.ExamAttemptStatus.FINISHED)
                .build();
        attemptRepository.save(a1);
        attemptRepository.save(a2);

        mockMvc.perform(post("/history/exams/delete-all")
                        .cookie(learnerCookie)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/history"))
                .andExpect(flash().attributeExists("historyMessage"));

        assertThat(attemptRepository.findByLearnerIdOrderBySubmittedAtDesc(learnerId)).isEmpty();
    }
}

