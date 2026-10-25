package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.TopicForm;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(TopicController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class TopicControllerTest {

    private static final String CSRF_TOKEN = "csrf-token-topic-test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    private Topic rootTopic;

    @BeforeEach
    void setUp() {
        rootTopic = new Topic();
        rootTopic.setId(1L);
        rootTopic.setName("1. Chào hỏi");
        rootTopic.setKanjiSection(false);

        when(topicService.findAllOrdered()).thenReturn(List.of(rootTopic));
        when(topicService.findRootTopics()).thenReturn(List.of(rootTopic));
    }

    @Test
    void listTopics_returnsOkAndView() throws Exception {
        mockMvc.perform(get("/topics"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/list"))
                .andExpect(model().attributeExists("topics"))
                .andExpect(content().string(containsString("1. Chào hỏi")));
    }

    @Test
    void createTopic_validDataWithCsrf_redirectsWithSuccess() throws Exception {
        when(topicService.create(any(TopicForm.class))).thenReturn(rootTopic);

        mockMvc.perform(post("/topics/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("name", "15. Đời sống gia đình")
                        .param("kanjiSection", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(topicService).create(any(TopicForm.class));
    }

    @Test
    void createTopic_invalidData_failsValidation() throws Exception {
        mockMvc.perform(post("/topics/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("name", "")) // tên trống
                .andExpect(status().isOk())
                .andExpect(view().name("topics/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }

    @Test
    void editTopic_existingId_showsForm() throws Exception {
        when(topicService.findById(1L)).thenReturn(Optional.of(rootTopic));
        when(topicService.toForm(rootTopic)).thenReturn(new TopicForm());

        mockMvc.perform(get("/topics/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/form"))
                .andExpect(model().attribute("isEdit", true))
                .andExpect(model().attribute("topicId", 1L));
    }

    @Test
    void editTopic_nonExistentId_redirectsWithError() throws Exception {
        when(topicService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/topics/999/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void deleteTopic_hasVocabulary_blockedWithError() throws Exception {
        doThrow(new IllegalStateException("Không thể xóa chủ đề vì đang có từ vựng thuộc chủ đề này."))
                .when(topicService).delete(1L);

        mockMvc.perform(post("/topics/1/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage", containsString("Không thể xóa chủ đề vì đang có từ vựng")));
    }

    @Test
    void deleteTopic_empty_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/topics/1/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(topicService).delete(1L);
    }

    @Test
    void deleteTopic_nonExistentId_redirectsWithError() throws Exception {
        doThrow(new NoSuchElementException("Không tìm thấy")).when(topicService).delete(999L);

        mockMvc.perform(post("/topics/999/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
