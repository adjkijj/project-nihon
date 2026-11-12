package com.nihon.history;

import com.nihon.repository.StudySessionRepository;
import com.nihon.service.StudyHistoryService;
import com.nihon.service.StudyHistoryService.AnswerItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StudyHistoryServiceTest {

    @Autowired
    StudyHistoryService service;
    @Autowired
    StudySessionRepository repo;

    private static final List<AnswerItem> ANS = List.of(new AnswerItem(null, null, true), new AnswerItem(null, null, false));

    @Test
    void isolationDeleteAndCleanup() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        assertThat(service.saveSessionSafe(a, "JA_VI", 999999L, ANS, 30)).isTrue();
        assertThat(service.saveSessionSafe(b, "TIMED_CHOICE", null, ANS, 10)).isTrue();
        assertThat(service.saveSessionSafe("not-a-uuid", "JA_VI", null, ANS, 1)).isFalse();
        assertThat(service.saveSessionSafe(a, "HACK", null, ANS, 1)).isFalse();

        assertThat(service.summary(a, "ALL", "ALL").totalQuestions()).isEqualTo(2);
        assertThat(service.summary(a, "ALL", "ALL").accuracyPercent()).isEqualTo(50);
        assertThat(service.findPage(a, "ALL", "ALL", 1_000_000).sessions()).hasSize(1);
        assertThat(service.findPage(a, "TIMED_CHOICE", "ALL", 1).sessions()).isEmpty();
        assertThat(service.weeklyAccuracy(a, "ALL")).hasSize(StudyHistoryService.WEEKS);

        assertThat(service.cleanup(LocalDateTime.now().plusDays(181))).isGreaterThanOrEqualTo(2);
        assertThat(service.summary(a, "ALL", "ALL").sessionCount()).isZero();

        service.saveSessionSafe(a, "JA_VI", null, ANS, 5);
        service.saveSessionSafe(b, "JA_VI", null, ANS, 5);
        assertThat(service.deleteAll(a)).isEqualTo(1);
        assertThat(service.summary(b, "ALL", "ALL").sessionCount()).isEqualTo(1);
    }

    @Test
    void pageAndWhitelists() {
        assertThat(StudyHistoryService.parsePage("99999999999999999999")).isEqualTo(1);
        assertThat(StudyHistoryService.parsePage("-5")).isEqualTo(1);
        assertThat(StudyHistoryService.normalizeMode("x' or 1=1")).isEqualTo("ALL");
        assertThat(StudyHistoryService.normalizeRange("30d")).isEqualTo("30D");
    }
}