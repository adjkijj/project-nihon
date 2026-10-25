package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.VocabularyForm;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(VocabularyController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class VocabularyControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-12345";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    private Topic testTopic;
    private Vocabulary testVocab;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("1. Chào hỏi");

        testVocab = new Vocabulary();
        testVocab.setId(10L);
        testVocab.setTopic(testTopic);
        testVocab.setJapaneseText("私（わたし）");
        testVocab.setReading("わたし");
        testVocab.setRomaji("watashi");
        testVocab.setMeaningVi("Tôi");

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    void listVocabulary_returnsOkAndView() throws Exception {
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testVocab)));

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attributeExists("vocabPage"))
                .andExpect(model().attributeExists("topics"))
                .andExpect(content().string(containsString("私（わたし）")));
    }

    @Test
    void showCreateForm_returnsFormView() throws Exception {
        mockMvc.perform(get("/vocabulary/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attribute("isEdit", false));
    }

    @Test
    void createVocabulary_validDataWithCsrf_redirectsWithSuccess() throws Exception {
        when(vocabularyService.create(any(VocabularyForm.class))).thenReturn(testVocab);

        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "水（みず）")
                        .param("reading", "みず")
                        .param("romaji", "mizu")
                        .param("meaningVi", "Nước"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(vocabularyService).create(any(VocabularyForm.class));
    }

    @Test
    void createVocabulary_missingCsrf_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        // no csrf param
                        .param("topicId", "1")
                        .param("japaneseText", "水（みず）")
                        .param("romaji", "mizu")
                        .param("meaningVi", "Nước"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createVocabulary_invalidData_failsValidationAndStaysOnForm() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "") // thiếu chủ đề
                        .param("japaneseText", "") // thiếu tiếng Nhật
                        .param("romaji", "") // thiếu romaji
                        .param("meaningVi", "")) // thiếu nghĩa
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "topicId", "japaneseText", "romaji", "meaningVi"));
    }

    @Test
    void editVocabulary_existingId_showsFormWithData() throws Exception {
        when(vocabularyService.findById(10L)).thenReturn(Optional.of(testVocab));
        when(vocabularyService.toForm(testVocab)).thenReturn(new VocabularyForm());

        mockMvc.perform(get("/vocabulary/10/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().attribute("isEdit", true))
                .andExpect(model().attribute("vocabId", 10L));
    }

    @Test
    void editVocabulary_nonExistentId_redirectsWithError() throws Exception {
        when(vocabularyService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/vocabulary/999/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void updateVocabulary_validData_redirectsWithSuccess() throws Exception {
        when(vocabularyService.update(eq(10L), any(VocabularyForm.class))).thenReturn(testVocab);

        mockMvc.perform(post("/vocabulary/10/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "私（わたし）")
                        .param("reading", "わたし")
                        .param("romaji", "watashi")
                        .param("meaningVi", "Tôi (cập nhật)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(vocabularyService).update(eq(10L), any(VocabularyForm.class));
    }

    @Test
    void updateVocabulary_nonExistentId_redirectsWithError() throws Exception {
        doThrow(new NoSuchElementException("Không tìm thấy")).when(vocabularyService).update(eq(999L), any(VocabularyForm.class));

        mockMvc.perform(post("/vocabulary/999/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "私")
                        .param("romaji", "watashi")
                        .param("meaningVi", "Tôi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void deleteVocabulary_existingId_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/vocabulary/10/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(vocabularyService).delete(10L);
    }

    @Test
    void deleteVocabulary_nonExistentId_redirectsWithError() throws Exception {
        doThrow(new NoSuchElementException("Không tìm thấy")).when(vocabularyService).delete(999L);

        mockMvc.perform(post("/vocabulary/999/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
