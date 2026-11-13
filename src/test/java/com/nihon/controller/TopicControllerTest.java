package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.TopicDetailDto;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private com.nihon.service.VerbService verbService;

    @MockitoBean
    private com.nihon.service.TopicTestService topicTestService;

    @MockitoBean
    private com.nihon.repository.GrammarTopicRepository grammarTopicRepository;

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

    @Test
    void editTopic_zeroAndNegativeId_redirectsWithInvalidIdMessage() throws Exception {
        mockMvc.perform(get("/topics/0/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));

        mockMvc.perform(get("/topics/-1/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));
    }

    @Test
    void createTopic_withControlCharInName_failsValidation() throws Exception {
        mockMvc.perform(post("/topics/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("name", "Chủ đề\nXuống dòng"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "name"));

        verify(topicService, never()).create(any());
    }

    @Test
    void createTopic_duplicateNameInSameParent_showsFieldErrorNot500() throws Exception {
        when(topicService.create(any(TopicForm.class)))
                .thenThrow(new IllegalArgumentException("Tên chủ đề đã tồn tại trong cùng nhóm."));

        mockMvc.perform(post("/topics/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("name", "1. Chào hỏi"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("Tên chủ đề đã tồn tại trong cùng nhóm.")));
    }

    @Test
    void postTopic_withFakeIdInBody_doesNotBindIdOrModifyOtherRecord() throws Exception {
        when(topicService.create(any(TopicForm.class))).thenReturn(rootTopic);
        when(topicService.update(eq(1L), any(TopicForm.class))).thenReturn(rootTopic);

        // 1. Tạo mới kèm id=999 giả trong body -> id trong form không được bind (null)
        mockMvc.perform(post("/topics/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("id", "999")
                        .param("name", "Chủ đề mới"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        ArgumentCaptor<TopicForm> createCaptor = ArgumentCaptor.forClass(TopicForm.class);
        verify(topicService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().getId()).isNull();

        // 2. Cập nhật /topics/1/edit kèm id=999 giả trong body -> vẫn chỉ cập nhật id=1L, form.id là null
        mockMvc.perform(post("/topics/1/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("id", "999")
                        .param("name", "Chủ đề cập nhật"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"));

        ArgumentCaptor<TopicForm> updateCaptor = ArgumentCaptor.forClass(TopicForm.class);
        verify(topicService).update(eq(1L), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isNull();
        verify(topicService, never()).update(eq(999L), any());
    }

    @Test
    void listTopics_scriptTagInTopicName_isEscapedInHtml() throws Exception {
        Topic xssTopic = new Topic();
        xssTopic.setId(2L);
        xssTopic.setName("<script>alert(1)</script>");
        when(topicService.findAllOrdered()).thenReturn(List.of(xssTopic));

        mockMvc.perform(get("/topics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void detailTopic_existingAndNonExistentId_behavesCorrectly() throws Exception {
        Topic child = new Topic();
        child.setId(2L);
        child.setName("1.1 Chào buổi sáng");
        child.setKanjiSection(false);

        TopicDetailDto detailDto = TopicDetailDto.builder()
                .id(1L)
                .name("1. Chào hỏi")
                .parentId(null)
                .parentName(null)
                .kanjiSection(false)
                .vocabularyCount(15L)
                .kanjiCount(0L)
                .children(List.of(child))
                .canDelete(false)
                .cannotDeleteReason("Không thể xóa vì đang có 1 chủ đề con, 15 từ vựng")
                .build();

        when(topicService.findDetailById(1L)).thenReturn(Optional.of(detailDto));
        when(topicService.findDetailById(999L)).thenReturn(Optional.empty());

        // 1. ID hợp lệ -> hiển thị trang chi tiết kèm liên kết /vocabulary?topicId=1 và /kanji?topicId=1
        mockMvc.perform(get("/topics/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/detail"))
                .andExpect(content().string(containsString("1. Chào hỏi")))
                .andExpect(content().string(containsString("1.1 Chào buổi sáng")))
                .andExpect(content().string(containsString("/vocabulary?topicId=1")))
                .andExpect(content().string(containsString("/kanji?topicId=1")));

        // 2. ID không tồn tại -> chuyển hướng với thông báo
        mockMvc.perform(get("/topics/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("errorMessage"));

        // 3. ID <= 0 -> chuyển hướng với thông báo "ID không hợp lệ"
        mockMvc.perform(get("/topics/0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));
    }

    @Test
    void moveTopic_validAndInvalidDirection_redirectsWithExpectedMessage() throws Exception {
        when(topicService.move(1L, "UP")).thenReturn(true);
        when(topicService.move(1L, "INVALID"))
                .thenThrow(new IllegalArgumentException("Hướng di chuyển không hợp lệ (chỉ chấp nhận UP hoặc DOWN)."));

        mockMvc.perform(post("/topics/1/move")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("direction", "UP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(post("/topics/1/move")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("direction", "INVALID"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage",
                        "Hướng di chuyển không hợp lệ (chỉ chấp nhận UP hoặc DOWN)."));
    }

    @Test
    void moveContent_validAndInvalidTarget_redirectsWithExpectedFlashMessage() throws Exception {
        when(topicService.moveContent(1L, 2L))
                .thenReturn(new TopicService.TopicMoveContentResult(5, 2, 1));
        when(topicService.moveContent(1L, 3L))
                .thenThrow(new IllegalArgumentException("Chủ đề đích phải cùng loại phân loại với chủ đề nguồn."));

        mockMvc.perform(post("/topics/1/move-content")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("targetTopicId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("successMessage",
                        containsString("Đã chuyển 5 từ vựng, 1 Kanji và gộp 2 bản ghi từ vựng trùng")));

        mockMvc.perform(post("/topics/1/move-content")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("targetTopicId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/topics"))
                .andExpect(flash().attribute("errorMessage",
                        "Chủ đề đích phải cùng loại phân loại với chủ đề nguồn."));
    }

    @Test
    void listTopics_searchFilterAndPaginationWhenOver50Topics() throws Exception {
        List<Topic> manyTopics = new ArrayList<>();
        for (int i = 1; i <= 55; i++) {
            Topic t = new Topic();
            t.setId((long) i);
            t.setName("Chủ đề số " + i);
            t.setSortOrder(i);
            t.setKanjiSection(i % 2 == 0);
            manyTopics.add(t);
        }
        when(topicService.findAllOrdered()).thenReturn(manyTopics);

        // Khi có 55 chủ đề (> 50): hiển thị phân trang (Trang 1 / 2)
        mockMvc.perform(get("/topics"))
                .andExpect(status().isOk())
                .andExpect(view().name("topics/list"))
                .andExpect(content().string(containsString("Trang sau")));

        // Lọc theo loại KANJI và từ khóa
        mockMvc.perform(get("/topics")
                        .param("keyword", "Chủ đề số 2")
                        .param("type", "KANJI"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chủ đề số 2")))
                .andExpect(content().string(not(containsString("Chủ đề số 1"))));
    }
}
