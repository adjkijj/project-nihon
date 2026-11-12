package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.dto.PracticeRecordDto;
import com.nihon.history.LearnerIdentityFilter;
import com.nihon.service.StudyHistoryService;
import com.nihon.service.StudyProgressService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class HistoryControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyHistoryService studyHistoryService;

    @Autowired
    private StudyProgressService studyProgressService;

    @Test
    @DisplayName("GET /history với cookie hợp lệ trả về 200, hiển thị tóm tắt và danh sách")
    void testGetHistory_withValidCookie() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId)))
                .andExpect(status().isOk())
                .andExpect(view().name("history/index"))
                .andExpect(model().attributeExists("summary", "modes", "ranges", "weeklyBars"))
                .andExpect(content().string(containsString("Lịch sử học tập")));
    }

    @Test
    @DisplayName("GET /history khi tắt cookie vẫn trả về 200, không bị lỗi 500")
    void testGetHistory_withoutCookie_noServerError() throws Exception {
        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("history/index"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(content().string(containsString("Lịch sử học tập")));
    }

    @Test
    @DisplayName("GET /history với page cực lớn không bị lỗi 500 (an toàn phân trang)")
    void testGetHistory_withLargePage_noServerError() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                        .param("page", "99999999999999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("history/index"));

        mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                        .param("page", "-10"))
                .andExpect(status().isOk())
                .andExpect(view().name("history/index"));
    }

    @Test
    @DisplayName("GET /history với filter mode và range lạ được chuẩn hóa an toàn (whitelist)")
    void testGetHistory_withInvalidFilters_normalizesToAll() throws Exception {
        String learnerId = UUID.randomUUID().toString();

        mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                        .param("mode", "HACK_MODE_SELECT_*")
                        .param("range", "1000YEARS"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("mode", "ALL"))
                .andExpect(model().attribute("range", "ALL"));
    }

    @Test
    @DisplayName("Cookie sai mẫu UUID được thay thế bằng mã mới hợp lệ")
    void testLearnerIdentityFilter_malformedCookie_replacedWithNewUuid() throws Exception {
        MvcResult result = mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, "malformed-cookie-12345")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains(LearnerIdentityFilter.COOKIE_NAME + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Max-Age=");
    }

    @Test
    @DisplayName("Tài nguyên tĩnh không bị áp dụng Set-Cookie của LearnerIdentityFilter")
    void testLearnerIdentityFilter_staticResourcesSkipped() throws Exception {
        mockMvc.perform(get("/css/history.css"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("POST /history/end-session lưu lịch sử và xóa lịch sử session hiện tại")
    void testEndSession_savesHistoryAndClearsSession() throws Exception {
        String learnerId = UUID.randomUUID().toString();
        MockHttpSession session = new MockHttpSession();

        // Thêm bản ghi luyện tập vào session
        com.nihon.dto.PracticeResultDto result = com.nihon.dto.PracticeResultDto.builder()
                .vocabId(1L)
                .japaneseText("本")
                .userMeaning("sách")
                .expectedMeaning("sách")
                .overallCorrect(true)
                .feedbackMessage("Chính xác!")
                .build();
        studyProgressService.recordJaViAttempt(session, result, null);

        assertThat(studyProgressService.getHistory(session)).hasSize(1);

        mockMvc.perform(post("/history/end-session")
                        .session(session)
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerId))
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/history"));

        // Lịch sử session đã được dọn
        assertThat(studyProgressService.getHistory(session)).isEmpty();

        // Lịch sử database của learnerId đã được lưu
        var summary = studyHistoryService.summary(learnerId, "ALL", "ALL");
        assertThat(summary.sessionCount()).isEqualTo(1);
        assertThat(summary.totalQuestions()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /history/delete-all chỉ xóa dữ liệu của chính người học, không ảnh hưởng người khác")
    void testDeleteAll_onlyDeletesOwnHistory() throws Exception {
        String learnerA = UUID.randomUUID().toString();
        String learnerB = UUID.randomUUID().toString();

        studyHistoryService.saveSessionSafe(learnerA, "JA_VI", null,
                java.util.List.of(new StudyHistoryService.AnswerItem(null, null, true)), 20);
        studyHistoryService.saveSessionSafe(learnerB, "JA_VI", null,
                java.util.List.of(new StudyHistoryService.AnswerItem(null, null, true)), 30);

        assertThat(studyHistoryService.summary(learnerA, "ALL", "ALL").sessionCount()).isEqualTo(1);
        assertThat(studyHistoryService.summary(learnerB, "ALL", "ALL").sessionCount()).isEqualTo(1);

        // Learner A gọi delete-all
        mockMvc.perform(post("/history/delete-all")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerA))
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/history"));

        // Learner A đã bị xóa, Learner B vẫn còn nguyên vẹn
        assertThat(studyHistoryService.summary(learnerA, "ALL", "ALL").sessionCount()).isZero();
        assertThat(studyHistoryService.summary(learnerB, "ALL", "ALL").sessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cách ly dữ liệu: Người học A không thấy dữ liệu của người học B")
    void testDataIsolation_learnerACannotSeeLearnerB() throws Exception {
        String learnerA = UUID.randomUUID().toString();
        String learnerB = UUID.randomUUID().toString();

        studyHistoryService.saveSessionSafe(learnerA, "JA_VI", null,
                java.util.List.of(new StudyHistoryService.AnswerItem(null, null, true)), 20);

        // Learner B xem history
        mockMvc.perform(get("/history")
                        .cookie(new Cookie(LearnerIdentityFilter.COOKIE_NAME, learnerB)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalElements", 0L));
    }

    @Test
    @DisplayName("Tắt cookie vẫn sử dụng bình thường các chức năng khác của ứng dụng")
    void testAppFunctionsNormally_whenCookieDisabled() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/vocabulary")).andExpect(status().isOk());
        mockMvc.perform(get("/kanji")).andExpect(status().isOk());
        mockMvc.perform(get("/topics")).andExpect(status().isOk());
        mockMvc.perform(get("/flashcards")).andExpect(status().isOk());
        mockMvc.perform(get("/practice")).andExpect(status().isOk());
        mockMvc.perform(get("/stats")).andExpect(status().isOk());
    }
}

