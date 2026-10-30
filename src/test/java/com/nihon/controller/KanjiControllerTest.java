package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.KanjiDetailDto;
import com.nihon.entity.Topic;
import com.nihon.entity.Vocabulary;
import com.nihon.service.KanjiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(KanjiController.class)
@Import({WebConfig.class, CsrfInterceptor.class})
class KanjiControllerTest {

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
}
