package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.VocabularyDetailDto;
import com.nihon.dto.VocabularyForm;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(controllers = {VocabularyController.class, GlobalExceptionHandler.class})
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
    private VocabularyRowDto testRowDto;

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

        testRowDto = VocabularyRowDto.builder()
                .id(10L)
                .japaneseText("私（わたし）")
                .reading("わたし")
                .romaji("watashi")
                .meaningVi("Tôi")
                .topics(List.of(testTopic))
                .allIds(List.of(10L))
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    void listVocabulary_returnsOkAndView() throws Exception {
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testRowDto)));

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attributeExists("vocabPage"))
                .andExpect(model().attributeExists("topics"))
                .andExpect(content().string(containsString("私（わたし）")));
    }

    @Test
    void listVocabulary_rendersMergedVocabularyWithMultipleTopics() throws Exception {
        Topic secondTopic = new Topic();
        secondTopic.setId(2L);
        secondTopic.setName("Kanji Bài 11 - Lặp lại thành phần");

        VocabularyRowDto mergedRow = VocabularyRowDto.builder()
                .id(10L)
                .japaneseText("新聞（しんぶん）")
                .reading("しんぶん")
                .romaji("shinbun")
                .meaningVi("Báo")
                .topics(List.of(testTopic, secondTopic))
                .allIds(List.of(10L, 11L))
                .build();

        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mergedRow)));

        mockMvc.perform(get("/vocabulary").param("keyword", "báo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kanji Bài 11 - Lặp lại thành phần")));
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
                .andExpect(model().attributeHasFieldErrors("form", "topicId", "japaneseText"));
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

    @Test
    void listVocabulary_maxIntPage_returns200AndEmptyList() throws Exception {
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/vocabulary").param("page", "2147483647"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attributeExists("vocabPage"))
                .andDo(result -> {
                    Page<?> page = (Page<?>) result.getModelAndView().getModel().get("vocabPage");
                    assertThat(page.getContent()).isEmpty();
                });
    }

    @Test
    void listVocabulary_negativePageAndZeroSize_returns200() throws Exception {
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testRowDto)));

        mockMvc.perform(get("/vocabulary").param("page", "-5").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"));
    }

    @Test
    void listVocabulary_keyword500Chars_truncatedAndDoesNotError() throws Exception {
        String longKeyword = "k".repeat(500);
        String expectedTruncated = "k".repeat(50);
        when(vocabularyService.search(isNull(), eq(expectedTruncated), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/vocabulary").param("keyword", longKeyword))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attribute("keyword", expectedTruncated));

        verify(vocabularyService).search(isNull(), eq(expectedTruncated), any(Pageable.class));
    }

    @Test
    void editVocabulary_zeroAndNegativeId_redirectsWithInvalidIdMessage() throws Exception {
        mockMvc.perform(get("/vocabulary/0/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));

        mockMvc.perform(get("/vocabulary/-1/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));

        verify(vocabularyService, never()).findById(any());
    }

    @Test
    void listVocabulary_nonExistentTopicId_showsErrorMessageAndEmptyList() throws Exception {
        when(topicService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/vocabulary").param("topicId", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attribute("errorMessage", "Chủ đề không tồn tại"))
                .andExpect(content().string(containsString("Chủ đề không tồn tại")))
                .andDo(result -> {
                    Page<?> page = (Page<?>) result.getModelAndView().getModel().get("vocabPage");
                    assertThat(page.getContent()).isEmpty();
                });

        verify(vocabularyService, never()).search(any(), any(), any());
    }

    @Test
    void listVocabulary_nonPositiveTopicId_dropsFilterAndShowsInvalidIdMessage() throws Exception {
        when(vocabularyService.search(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testRowDto)));

        mockMvc.perform(get("/vocabulary").param("topicId", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/list"))
                .andExpect(model().attribute("errorMessage", "ID không hợp lệ"))
                .andExpect(model().attribute("selectedTopicId", (Object) null));

        verify(vocabularyService).search(isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void createVocabulary_duplicateThrowsDataIntegrityViolation_returns409WithGenericMessage() throws Exception {
        when(vocabularyService.create(any(VocabularyForm.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '私' for key 'uk_vocabulary_topic_jp'"));

        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "私（わたし）")
                        .param("reading", "わたし")
                        .param("romaji", "watashi")
                        .param("meaningVi", "Tôi"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("errorMessage", "Dữ liệu bị trùng hoặc vi phạm ràng buộc"))
                .andExpect(content().string(containsString("Dữ liệu bị trùng hoặc vi phạm ràng buộc")))
                .andExpect(content().string(not(containsString("uk_vocabulary_topic_jp"))));
    }

    @Test
    void createVocabulary_duplicateWordInSameTopic_showsFieldErrorAndNot500() throws Exception {
        when(vocabularyService.create(any(VocabularyForm.class)))
                .thenThrow(new IllegalArgumentException("Từ này đã tồn tại trong chủ đề đã chọn."));

        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "私（わたし）")
                        .param("reading", "わたし")
                        .param("romaji", "watashi")
                        .param("meaningVi", "Tôi"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "topicId"))
                .andExpect(content().string(containsString("Từ này đã tồn tại trong chủ đề đã chọn.")));
    }

    @Test
    void createVocabulary_japaneseTextOnlyLatin_readingWithLatin_romajiWithJapanese_failsValidation() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "watashi") // chỉ chữ Latin -> lỗi @JapaneseText
                        .param("reading", "わたしabc") // có chữ Latin -> lỗi @KanaReading
                        .param("romaji", "watashi わたし") // có chữ Nhật -> lỗi @RomajiText
                        .param("meaningVi", "Tôi"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "japaneseText", "reading", "romaji"));

        verify(vocabularyService, never()).create(any());
    }

    @Test
    void createVocabulary_withScriptTag_failsValidationOnTextFields() throws Exception {
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "私<script>alert(1)</script>")
                        .param("reading", "わたし<script>")
                        .param("romaji", "<script>alert(1)</script>")
                        .param("meaningVi", "Tôi <script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "japaneseText", "reading", "romaji", "meaningVi"));

        verify(vocabularyService, never()).create(any());
    }

    @Test
    void postVocabulary_withFakeIdInBody_doesNotBindIdOrModifyOtherRecord() throws Exception {
        when(vocabularyService.create(any(VocabularyForm.class))).thenReturn(testVocab);
        when(vocabularyService.update(eq(10L), any(VocabularyForm.class))).thenReturn(testVocab);

        // 1. Tạo mới kèm id=999 giả trong body -> form.id vẫn là null
        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("id", "999")
                        .param("topicId", "1")
                        .param("japaneseText", "水（みず）")
                        .param("reading", "みず"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"));

        ArgumentCaptor<VocabularyForm> createCaptor = ArgumentCaptor.forClass(VocabularyForm.class);
        verify(vocabularyService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().getId()).isNull();

        // 2. Cập nhật /vocabulary/10/edit kèm id=999 giả trong body -> vẫn chỉ gọi update(10L, ...), form.id là null
        mockMvc.perform(post("/vocabulary/10/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("id", "999")
                        .param("topicId", "1")
                        .param("japaneseText", "私（わたし）")
                        .param("reading", "わたし"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"));

        ArgumentCaptor<VocabularyForm> updateCaptor = ArgumentCaptor.forClass(VocabularyForm.class);
        verify(vocabularyService).update(eq(10L), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isNull();
        verify(vocabularyService, never()).update(eq(999L), any());
    }

    @Test
    void createAndUpdateVocabulary_kanjiWithoutReading_addsWarningFlashMessage() throws Exception {
        Vocabulary kanjiNoReading = new Vocabulary();
        kanjiNoReading.setId(25L);
        kanjiNoReading.setTopic(testTopic);
        kanjiNoReading.setJapaneseText("日本");
        kanjiNoReading.setReading(null);
        kanjiNoReading.setHasKanji(true);

        when(vocabularyService.create(any(VocabularyForm.class))).thenReturn(kanjiNoReading);
        when(vocabularyService.update(eq(25L), any(VocabularyForm.class))).thenReturn(kanjiNoReading);

        mockMvc.perform(post("/vocabulary/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "日本")
                        .param("reading", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("warningMessage",
                        "Từ có Kanji nhưng chưa có cách đọc, phần luyện tập sẽ không kiểm tra cách đọc."));

        mockMvc.perform(post("/vocabulary/25/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "日本")
                        .param("reading", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("warningMessage",
                        "Từ có Kanji nhưng chưa có cách đọc, phần luyện tập sẽ không kiểm tra cách đọc."));
    }

    @Test
    void listVocabulary_whenFilteredAndUnfiltered_rendersCorrectConfirmationMessageAndScope() throws Exception {
        Topic secondTopic = new Topic();
        secondTopic.setId(2L);
        secondTopic.setName("2. Mua sắm");

        VocabularyRowDto mergedRow = VocabularyRowDto.builder()
                .id(10L)
                .japaneseText("店（みせ）")
                .reading("みせ")
                .romaji("mise")
                .meaningVi("Cửa hàng")
                .topics(List.of(testTopic, secondTopic))
                .allIds(List.of(10L, 11L))
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic, secondTopic));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mergedRow)));

        // 1. Khi không lọc chủ đề: hộp xác nhận ghi "Từ này thuộc 2 chủ đề. Xóa khỏi tất cả?" và scope=ALL
        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Từ này thuộc 2 chủ đề. Xóa khỏi tất cả?")))
                .andExpect(content().string(containsString("name=\"scope\" value=\"ALL\"")))
                .andExpect(content().string(containsString("name=\"allIds\" value=\"10,11\"")));

        // 2. Khi đang lọc theo chủ đề 1 ("1. Chào hỏi"): hộp xác nhận ghi "Xóa từ này khỏi chủ đề 1. Chào hỏi?" và scope=TOPIC
        mockMvc.perform(get("/vocabulary").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Xóa từ này khỏi chủ đề 1. Chào hỏi?")))
                .andExpect(content().string(containsString("name=\"scope\" value=\"TOPIC\"")))
                .andExpect(content().string(containsString("name=\"allIds\" value=\"10,11\"")));
    }

    @Test
    void deleteVocabulary_withScopeTopicAndAll_callsExpectedServiceMethodsAndShowsRecordCount() throws Exception {
        when(vocabularyService.deleteInTopic(10L)).thenReturn(1);
        when(vocabularyService.deleteGroup(eq(10L), eq(List.of(10L, 11L, 12L)))).thenReturn(3);

        // Xóa khi đang lọc chủ đề (scope=TOPIC)
        mockMvc.perform(post("/vocabulary/10/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("scope", "TOPIC")
                        .param("allIds", "10,11,12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage", "Đã xóa 1 bản ghi từ vựng thành công."));

        verify(vocabularyService).deleteInTopic(10L);

        // Xóa khi không lọc chủ đề (scope=ALL)
        mockMvc.perform(post("/vocabulary/10/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("scope", "ALL")
                        .param("allIds", "10,11,12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage", "Đã xóa 3 bản ghi từ vựng thành công."));

        verify(vocabularyService).deleteGroup(eq(10L), eq(List.of(10L, 11L, 12L)));
    }

    @Test
    void updateVocabulary_withGroupParams_callsUpdateGroupAndShowsUpdatedRecordCount() throws Exception {
        Vocabulary v1 = new Vocabulary();
        v1.setId(10L);
        v1.setJapaneseText("店（みせ）");
        v1.setReading("みせ");
        v1.setHasKanji(true);

        Vocabulary v2 = new Vocabulary();
        v2.setId(11L);
        v2.setJapaneseText("店（みせ）");
        v2.setReading("みせ");
        v2.setHasKanji(true);

        when(vocabularyService.updateGroup(eq(10L), any(VocabularyForm.class)))
                .thenReturn(List.of(v1, v2));

        mockMvc.perform(post("/vocabulary/10/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "1")
                        .param("japaneseText", "店（みせ）")
                        .param("reading", "みせ")
                        .param("romaji", "mise")
                        .param("meaningVi", "Cửa tiệm")
                        .param("scope", "ALL")
                        .param("allIds", "10,11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage", "Đã cập nhật 2 bản ghi từ vựng thành công!"));

        verify(vocabularyService).updateGroup(eq(10L), any(VocabularyForm.class));
    }

    @Test
    void showCreateForm_withTopicIdAndJapaneseTextParams_prefillsAndNormalizesForm() throws Exception {
        mockMvc.perform(get("/vocabulary/new")
                        .param("topicId", "1")
                        .param("japaneseText", "  魚\u200B  "))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/form"))
                .andDo(result -> {
                    VocabularyForm form = (VocabularyForm) result.getModelAndView().getModel().get("form");
                    assertThat(form.getTopicId()).isEqualTo(1L);
                    assertThat(form.getJapaneseText()).isEqualTo("魚");
                });
    }

    @Test
    void detailVocabulary_wordBelongingToTwoTopics_rendersBothTopicsAndKanjiLink() throws Exception {
        Topic secondTopic = new Topic();
        secondTopic.setId(2L);
        secondTopic.setName("2. Trường học");

        VocabularyDetailDto detail = VocabularyDetailDto.builder()
                .id(10L)
                .japaneseText("先生（せんせい）")
                .reading("せんせい")
                .romaji("sensei")
                .meaningVi("Giáo viên")
                .hasKanji(true)
                .createdAt(LocalDateTime.of(2026, 1, 15, 10, 30))
                .updatedAt(LocalDateTime.of(2026, 1, 16, 14, 0))
                .topics(List.of(testTopic, secondTopic))
                .allIds(List.of(10L, 11L))
                .kanjis(List.of(
                        VocabularyDetailDto.KanjiInWordDto.builder()
                                .character("先")
                                .kanjiId(5L)
                                .hanViet("TIÊN")
                                .meaningVi("Trước")
                                .build(),
                        VocabularyDetailDto.KanjiInWordDto.builder()
                                .character("生")
                                .kanjiId(null)
                                .build()
                ))
                .build();

        when(vocabularyService.findDetailById(10L)).thenReturn(Optional.of(detail));
        when(vocabularyService.findDetailById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/vocabulary/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("vocabulary/detail"))
                .andExpect(model().attributeExists("vocab"))
                .andExpect(content().string(containsString("先生（せんせい）")))
                .andExpect(content().string(containsString("1. Chào hỏi")))
                .andExpect(content().string(containsString("2. Trường học")))
                .andExpect(content().string(containsString("/kanji/5")));

        // ID không hợp lệ hoặc không tồn tại -> chuyển hướng kèm thông báo lỗi
        mockMvc.perform(get("/vocabulary/-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));

        mockMvc.perform(get("/vocabulary/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void bulkDelete_threeWordsSucceeds_and201IdsRejected() throws Exception {
        when(vocabularyService.bulkDelete(eq(List.of(10L, 11L, 12L)), eq("TOPIC")))
                .thenReturn(new VocabularyService.BulkDeleteResult(3, 0));

        mockMvc.perform(post("/vocabulary/bulk-delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("ids", "10,11,12")
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage", "Đã xóa 3 bản ghi từ vựng thành công."));

        verify(vocabularyService).bulkDelete(eq(List.of(10L, 11L, 12L)), eq("TOPIC"));

        // 201 IDs bị từ chối
        String ids201Csv = LongStream.rangeClosed(1, 201)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        when(vocabularyService.bulkDelete(anyList(), anyString()))
                .thenThrow(new IllegalArgumentException("Chỉ được phép thao tác tối đa 200 từ vựng mỗi lần."));

        mockMvc.perform(post("/vocabulary/bulk-delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("ids", ids201Csv)
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("errorMessage", "Chỉ được phép thao tác tối đa 200 từ vựng mỗi lần."));
    }

    @Test
    void bulkMove_withDuplicateWordAtTarget_redirectsWithMovedAndMergedCounts() throws Exception {
        when(vocabularyService.bulkMove(eq(List.of(10L, 11L)), eq(2L), eq("TOPIC")))
                .thenReturn(new VocabularyService.BulkMoveResult(1, 1, 0));

        mockMvc.perform(post("/vocabulary/bulk-move")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("ids", "10,11")
                        .param("targetTopicId", "2")
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage",
                        "Đã chuyển 1 bản ghi từ vựng, gộp 1 bản ghi trùng (bỏ qua 0 ID không hợp lệ)."));

        verify(vocabularyService).bulkMove(eq(List.of(10L, 11L)), eq(2L), eq("TOPIC"));
    }

    @Test
    void bulkOperations_withInvalidIdsInList_skipsWithout500() throws Exception {
        when(vocabularyService.bulkDelete(eq(List.of(10L, -5L, -1L, 999999L)), eq("TOPIC")))
                .thenReturn(new VocabularyService.BulkDeleteResult(1, 3));

        mockMvc.perform(post("/vocabulary/bulk-delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("ids", "10,-5,abc,999999")
                        .param("scope", "TOPIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vocabulary"))
                .andExpect(flash().attribute("successMessage",
                        "Đã xóa 1 bản ghi từ vựng (bỏ qua 3 ID không hợp lệ hoặc không tồn tại)."));

        verify(vocabularyService).bulkDelete(eq(List.of(10L, -5L, -1L, 999999L)), eq("TOPIC"));
    }

    @Test
    void bulkDeleteAndBulkMove_missingCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/vocabulary/bulk-delete")
                        .param("ids", "10,11"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/vocabulary/bulk-move")
                        .param("ids", "10,11")
                        .param("targetTopicId", "2"))
                .andExpect(status().isForbidden());

        verify(vocabularyService, never()).bulkDelete(anyList(), anyString());
        verify(vocabularyService, never()).bulkMove(anyList(), any(), anyString());
    }
}
