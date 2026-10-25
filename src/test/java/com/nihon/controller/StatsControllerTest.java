package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeRecordDto;
import com.nihon.dto.StudyProgressSummaryDto;
import com.nihon.entity.Topic;
import com.nihon.repository.KanjiEntryRepository;
import com.nihon.repository.TopicRepository;
import com.nihon.repository.VocabularyRepository;
import com.nihon.service.StudyProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StatsController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class StatsControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-xyz";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyRepository vocabularyRepository;

    @MockitoBean
    private TopicRepository topicRepository;

    @MockitoBean
    private KanjiEntryRepository kanjiEntryRepository;

    @MockitoBean
    private StudyProgressService studyProgressService;

    @Test
    void testStatsPageLoadsSuccessfullyWithStudyProgress() throws Exception {
        when(vocabularyRepository.count()).thenReturn(100L);
        when(topicRepository.count()).thenReturn(5L);
        when(kanjiEntryRepository.count()).thenReturn(20L);
        when(vocabularyRepository.countByHasKanjiTrue()).thenReturn(60L);
        when(vocabularyRepository.countByHasKanjiFalse()).thenReturn(40L);

        Topic topic1 = new Topic();
        topic1.setId(1L);
        topic1.setName("Chủ đề 1");
        when(topicRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(topic1));
        when(vocabularyRepository.countByTopicId(1L)).thenReturn(25L);

        PracticeRecordDto mistake = PracticeRecordDto.builder()
                .id("1")
                .vocabId(1L)
                .topicId(1L)
                .topicName("Chủ đề 1")
                .mode("JA_VI")
                .questionPrompt("私（わたし）")
                .userAnswer("watashi - bạn")
                .expectedAnswer("わたし - Tôi")
                .correct(false)
                .feedbackMessage("Chưa đúng")
                .timestamp(LocalDateTime.now())
                .build();

        StudyProgressSummaryDto summary = StudyProgressSummaryDto.builder()
                .totalAnswered(5)
                .correctCount(3)
                .incorrectCount(2)
                .accuracyPercentage(60)
                .records(List.of(mistake))
                .incorrectRecords(List.of(mistake))
                .currentModeFilter("ALL")
                .build();

        when(studyProgressService.getSummary(any(), eq("ALL"), eq(null))).thenReturn(summary);

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(view().name("stats"))
                .andExpect(model().attribute("totalVocab", 100L))
                .andExpect(model().attribute("totalTopics", 5L))
                .andExpect(model().attribute("totalKanji", 20L))
                .andExpect(model().attribute("vocabWithKanjiCount", 60L))
                .andExpect(model().attribute("vocabKanaOnlyCount", 40L))
                .andExpect(model().attribute("kanjiPercentage", 60))
                .andExpect(model().attribute("kanaPercentage", 40))
                .andExpect(model().attributeExists("topicStats"))
                .andExpect(model().attributeExists("progress"))
                .andExpect(content().string(containsString("Project Nihon")))
                .andExpect(content().string(containsString("Phiên làm việc (Session Scope)")));
    }

    @Test
    void testStatsWithEmptyHistory() throws Exception {
        when(vocabularyRepository.count()).thenReturn(0L);
        when(topicRepository.count()).thenReturn(0L);
        when(kanjiEntryRepository.count()).thenReturn(0L);
        when(vocabularyRepository.countByHasKanjiTrue()).thenReturn(0L);
        when(vocabularyRepository.countByHasKanjiFalse()).thenReturn(0L);
        when(topicRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        StudyProgressSummaryDto emptySummary = StudyProgressSummaryDto.builder()
                .totalAnswered(0)
                .correctCount(0)
                .incorrectCount(0)
                .accuracyPercentage(0)
                .records(Collections.emptyList())
                .incorrectRecords(Collections.emptyList())
                .currentModeFilter("ALL")
                .build();

        when(studyProgressService.getSummary(any(), any(), any())).thenReturn(emptySummary);

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(view().name("stats"))
                .andExpect(model().attribute("totalVocab", 0L))
                .andExpect(content().string(containsString("Chưa có dữ liệu làm bài trong phạm vi đã chọn")));
    }

    @Test
    void testResetProgress() throws Exception {
        mockMvc.perform(post("/stats/reset")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/stats"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(studyProgressService).clearProgress(any());
    }
}
