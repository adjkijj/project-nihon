package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.KanjiDetailDto;
import com.nihon.dto.KanjiForm;
import com.nihon.entity.KanjiEntry;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.KanjiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(KanjiController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class KanjiControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token-kanji-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KanjiService kanjiService;

    private KanjiDetailDto kanjiFish;
    private KanjiDetailDto kanjiSchoolMissingKun;
    private Topic kanjiTopic;

    @BeforeEach
    void setUp() {
        kanjiTopic = new Topic();
        kanjiTopic.setId(5L);
        kanjiTopic.setName("Kanji Lesson 5");
        kanjiTopic.setKanjiSection(true);

        kanjiFish = KanjiDetailDto.builder()
                .id(1L)
                .character("魚")
                .hanViet("NGƯ")
                .meaningVi("Cá")
                .onyomi("ギョ")
                .kunyomi("さかな")
                .topicId(5L)
                .topicName("Kanji Lesson 5")
                .sourceLine(1244)
                .build();

        kanjiSchoolMissingKun = KanjiDetailDto.builder()
                .id(2L)
                .character("校")
                .hanViet("GIÁO, HIỆU")
                .meaningVi("Trường học")
                .onyomi("こう、きょう")
                .kunyomi("-")
                .topicId(5L)
                .topicName("Kanji Lesson 5")
                .sourceLine(1800)
                .build();
    }

    @Test
    @DisplayName("Hiển thị trang danh sách Kanji riêng biệt thành công")
    void testListDisplay() throws Exception {
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish, kanjiSchoolMissingKun)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(model().attributeExists("kanjiPage", "topics", "totalCount", "activeNav"))
                .andExpect(content().string(containsString("Khu Vực Tra Cứu Kanji")))
                .andExpect(content().string(containsString("魚")))
                .andExpect(content().string(containsString("NGƯ")))
                .andExpect(content().string(containsString("Cá")));
    }

    @Test
    @DisplayName("Tìm kiếm Kanji bằng ký tự tiếng Nhật (魚)")
    void testSearchByKanjiCharacter() throws Exception {
        when(kanjiService.search(any(), eq("魚"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("keyword", "魚"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(content().string(containsString("魚")))
                .andExpect(content().string(containsString("NGƯ")));

        verify(kanjiService).search(any(), eq("魚"), any(Pageable.class));
    }

    @Test
    @DisplayName("Tìm kiếm Kanji bằng âm Hán Việt (NGƯ)")
    void testSearchByHanViet() throws Exception {
        when(kanjiService.search(any(), eq("NGƯ"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("keyword", "NGƯ"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(content().string(containsString("NGƯ")));

        verify(kanjiService).search(any(), eq("NGƯ"), any(Pageable.class));
    }

    @Test
    @DisplayName("Tìm kiếm Kanji bằng nghĩa tiếng Việt (Cá)")
    void testSearchByVietnameseMeaning() throws Exception {
        when(kanjiService.search(any(), eq("Cá"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("keyword", "Cá"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(content().string(containsString("Cá")));

        verify(kanjiService).search(any(), eq("Cá"), any(Pageable.class));
    }

    @Test
    @DisplayName("Tìm kiếm Kanji bằng âm On Katakana (ギョ)")
    void testSearchByOnyomiKatakana() throws Exception {
        when(kanjiService.search(any(), eq("ギョ"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("keyword", "ギョ"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(content().string(containsString("ギョ")));

        verify(kanjiService).search(any(), eq("ギョ"), any(Pageable.class));
    }

    @Test
    @DisplayName("Tìm kiếm Kanji bằng âm Kun Hiragana (さかな)")
    void testSearchByKunyomiHiragana() throws Exception {
        when(kanjiService.search(any(), eq("さかな"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("keyword", "さかな"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(content().string(containsString("さかな")));

        verify(kanjiService).search(any(), eq("さかな"), any(Pageable.class));
    }

    @Test
    @DisplayName("Lọc danh sách Kanji theo bài học chủ đề")
    void testFilterByTopic() throws Exception {
        when(kanjiService.search(eq(5L), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("topicId", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(model().attribute("selectedTopicId", 5L));

        verify(kanjiService).search(eq(5L), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Xử lý dữ liệu thiếu: hiển thị đúng khi âm Kun ghi '-' trong nguồn")
    void testMissingKunyomiDisplay() throws Exception {
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiSchoolMissingKun)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("校")))
                .andExpect(content().string(containsString("- (Không có)")));
    }

    @Test
    @DisplayName("Hiển thị chuẩn xác ký tự tiếng Nhật (Kanji, Hiragana, Katakana, UTF-8)")
    void testJapaneseCharactersEncoding() throws Exception {
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji"))
                .andExpect(status().isOk())
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string(containsString("魚")))
                .andExpect(content().string(containsString("ギョ")))
                .andExpect(content().string(containsString("さかな")));
    }

    @Test
    @DisplayName("Mở xem chi tiết Kanji thành công kèm từ vựng liên quan")
    void testDetailSuccess() throws Exception {
        Vocabulary sampleVocab = new Vocabulary();
        sampleVocab.setId(101L);
        sampleVocab.setJapaneseText("魚（さかな）");
        sampleVocab.setReading("さかな");
        sampleVocab.setRomaji("sakana");
        sampleVocab.setMeaningVi("Cá");
        sampleVocab.setTopic(kanjiTopic);

        when(kanjiService.findDetailById(1L)).thenReturn(Optional.of(kanjiFish));
        when(kanjiService.getRelatedVocabulary(eq("魚"), any())).thenReturn(List.of(sampleVocab));
        when(kanjiService.getRelatedVocabulary("魚")).thenReturn(List.of(sampleVocab));

        mockMvc.perform(get("/kanji/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/detail"))
                .andExpect(model().attributeExists("kanji", "relatedVocabularies"))
                .andExpect(content().string(containsString("魚")))
                .andExpect(content().string(containsString("NGƯ")))
                .andExpect(content().string(containsString("ギョ")))
                .andExpect(content().string(containsString("さかな")))
                .andExpect(content().string(containsString("魚（さかな）")))
                .andExpect(content().string(containsString("sakana")));
    }

    @Test
    @DisplayName("Xem chi tiết Kanji với ID không tồn tại sẽ chuyển hướng an toàn kèm thông báo lỗi")
    void testDetailNotFound() throws Exception {
        when(kanjiService.findDetailById(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/kanji/999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Chống XSS: từ khóa độc hại được escape an toàn bởi Thymeleaf")
    void testXssEscapingInSearchKeyword() throws Exception {
        String xssPayload = "<script>alert('xss')</script>";
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of());
        when(kanjiService.countTotal()).thenReturn(0L);

        mockMvc.perform(get("/kanji").param("keyword", xssPayload))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))));
    }

    @Test
    @DisplayName("Lọc Kanji với topicId không tồn tại: hiện thông báo 'Chủ đề không tồn tại' và danh sách rỗng")
    void testFilterByNonExistentTopic() throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.existsTopicById(999L)).thenReturn(false);
        when(kanjiService.countTotal()).thenReturn(126L);

        mockMvc.perform(get("/kanji").param("topicId", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(model().attribute("errorMessage", "Chủ đề không tồn tại"))
                .andExpect(content().string(containsString("Chủ đề không tồn tại")))
                .andExpect(content().string(containsString("Không tìm thấy ký tự Kanji phù hợp")));
    }

    @Test
    @DisplayName("Xem chi tiết Kanji với ID <= 0 chuyển hướng với thông báo 'ID không hợp lệ'")
    void testDetailInvalidId() throws Exception {
        mockMvc.perform(get("/kanji/0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));

        mockMvc.perform(get("/kanji/-5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attribute("errorMessage", "ID không hợp lệ"));
    }

    @Test
    @DisplayName("1. Tạo Kanji hợp lệ với CSRF chuyển hướng về danh sách kèm thông báo thành công")
    void createKanji_validDataWithCsrf_redirectsWithSuccess() throws Exception {
        KanjiEntry saved = new KanjiEntry();
        saved.setId(10L);
        saved.setCharacter("魚");
        saved.setTopic(kanjiTopic);

        when(kanjiService.create(any(KanjiForm.class))).thenReturn(saved);

        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", "魚")
                        .param("hanViet", "NGƯ")
                        .param("meaningVi", "Cá")
                        .param("onyomi", "ギョ")
                        .param("kunyomi", "さかな"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(kanjiService).create(any(KanjiForm.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "魚肉", "あ", "ア", "A", "1"})
    @DisplayName("2. character nhiều ký tự, ký tự kana, chữ Latin, số, chuỗi rỗng bị chặn bởi @SingleKanji")
    void createKanji_invalidCharacter_failsValidation(String invalidChar) throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));

        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", invalidChar)
                        .param("hanViet", "NGƯ")
                        .param("meaningVi", "Cá"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "character"));

        verify(kanjiService, never()).create(any());
    }

    @Test
    @DisplayName("3. character trùng báo lỗi ở trường character; sửa không đổi gì thì lưu thành công")
    void createAndUpdateKanji_duplicateCharacterFails_unchangedSelfSucceeds() throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.create(any(KanjiForm.class)))
                .thenThrow(new IllegalArgumentException("Kanji này đã tồn tại."));

        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", "魚")
                        .param("hanViet", "NGƯ")
                        .param("meaningVi", "Cá"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "character"))
                .andExpect(content().string(containsString("Kanji này đã tồn tại.")));

        // Sửa không đổi gì -> lưu thành công
        KanjiEntry existing = new KanjiEntry();
        existing.setId(1L);
        existing.setCharacter("魚");
        existing.setTopic(kanjiTopic);
        when(kanjiService.findById(1L)).thenReturn(Optional.of(existing));
        when(kanjiService.update(eq(1L), any(KanjiForm.class))).thenReturn(existing);

        mockMvc.perform(post("/kanji/1/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", "魚")
                        .param("hanViet", "NGƯ")
                        .param("meaningVi", "Cá")
                        .param("onyomi", "ギョ")
                        .param("kunyomi", "さかな"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("4. topicId trỏ tới chủ đề không phải nhánh Kanji báo lỗi ở trường topicId")
    void createKanji_nonKanjiTopic_showsFieldErrorOnTopicId() throws Exception {
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(kanjiTopic));
        when(kanjiService.create(any(KanjiForm.class)))
                .thenThrow(new IllegalArgumentException("Chủ đề đã chọn không thuộc nhánh Kanji."));

        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("topicId", "2")
                        .param("character", "魚")
                        .param("hanViet", "NGƯ")
                        .param("meaningVi", "Cá"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("form", "topicId"))
                .andExpect(content().string(containsString("Chủ đề đã chọn không thuộc nhánh Kanji.")));
    }

    @Test
    @DisplayName("5. Xóa Kanji thành công; trang chi tiết hiển thị nút Thêm từ cần nhớ và hộp xác nhận ghi số từ vựng giữ lại")
    void deleteKanjiAndDetailView_showsAddWordLinkAndRetainedVocabCount() throws Exception {
        Vocabulary sampleVocab = new Vocabulary();
        sampleVocab.setId(101L);
        sampleVocab.setJapaneseText("魚（さかな）");
        sampleVocab.setReading("さかな");
        sampleVocab.setRomaji("sakana");
        sampleVocab.setMeaningVi("Cá");
        sampleVocab.setTopic(kanjiTopic);

        when(kanjiService.findDetailById(1L)).thenReturn(Optional.of(kanjiFish));
        when(kanjiService.getRelatedVocabulary(eq("魚"), any())).thenReturn(List.of(sampleVocab));

        mockMvc.perform(get("/kanji/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/vocabulary/new?topicId=5&amp;japaneseText=")))
                .andExpect(content().string(containsString("1 từ vựng liên quan còn giữ lại")));

        mockMvc.perform(post("/kanji/1/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kanji"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(kanjiService).delete(1L);
    }

    @Test
    @DisplayName("6. POST tạo, sửa, xóa Kanji thiếu CSRF bị chặn (403 Forbidden) và không cho bind id từ client")
    void postKanji_missingCsrfBlocked_andFakeIdNotBound() throws Exception {
        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", "魚"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/kanji/1/edit")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("topicId", "5")
                        .param("character", "魚"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/kanji/1/delete")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN))
                .andExpect(status().isForbidden());

        // Gửi id=999 giả trong body khi tạo mới -> form.id vẫn là null
        KanjiEntry saved = new KanjiEntry();
        saved.setId(1L);
        saved.setCharacter("魚");
        when(kanjiService.create(any(KanjiForm.class))).thenReturn(saved);

        mockMvc.perform(post("/kanji/new")
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param(CsrfInterceptor.CSRF_PARAM_NAME, CSRF_TOKEN)
                        .param("id", "999")
                        .param("topicId", "5")
                        .param("character", "魚"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<KanjiForm> captor = ArgumentCaptor.forClass(KanjiForm.class);
        verify(kanjiService).create(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }
}
