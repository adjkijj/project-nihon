package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.ConstituentKanjiDto;
import com.nihon.dto.KanjiDetailDto;
import com.nihon.dto.KanjiSortOption;
import com.nihon.dto.TopicGroupDto;
import com.nihon.dto.VocabQuickFilter;
import com.nihon.dto.VocabViewMode;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.KanjiService;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử tích hợp cho F18, F21, F24: Tra cứu nâng cao, bộ lọc âm On/Kun romaji,
 * sắp xếp, phân nhóm bài học, bảng từ vựng đầy đủ và bộ lọc nhanh.
 */
@WebMvcTest(controllers = {KanjiController.class, VocabularyController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class LookupAndFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KanjiService kanjiService;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    private Topic testTopicParent;
    private Topic testTopicChild;
    private KanjiDetailDto testKanjiFish;
    private VocabularyRowDto testVocabFish;

    @BeforeEach
    void setUp() {
        testTopicParent = new Topic();
        testTopicParent.setId(1L);
        testTopicParent.setName("Kanji N5");

        testTopicChild = new Topic();
        testTopicChild.setId(2L);
        testTopicChild.setName("Bài 1");
        testTopicChild.setParent(testTopicParent);

        testKanjiFish = KanjiDetailDto.builder()
                .id(101L)
                .character("魚")
                .onyomi("ギョ")
                .kunyomi("さかな")
                .hanViet("NGƯ")
                .meaningVi("Cá")
                .topicId(2L)
                .topicName("Bài 1")
                .build();

        testVocabFish = VocabularyRowDto.builder()
                .id(201L)
                .japaneseText("魚（さかな）")
                .reading("さかな")
                .romaji("sakana")
                .meaningVi("Con cá")
                .hasKanji(true)
                .topics(List.of(testTopicChild))
                .constituentKanji(List.of(
                        ConstituentKanjiDto.builder()
                                .character("魚")
                                .hanViet("NGƯ")
                                .kanjiId(101L)
                                .existsInKanjiEntry(true)
                                .build()
                ))
                .build();
    }

    // =========================================================================
    // 1. Kanji Lookup & Filters (F18, F24)
    // =========================================================================

    @Test
    @DisplayName("F18: Tìm kiếm Kanji theo âm On/Kun bằng Romaji Hepburn (gyo -> Katakana/Hiragana)")
    void kanjiSearch_byRomajiReading_invokesServiceWithReading() throws Exception {
        when(kanjiService.search(any(), any(), eq("gyo"), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testKanjiFish)));
        when(kanjiService.getKanjiTopics()).thenReturn(List.of(testTopicChild));
        when(kanjiService.countTotal()).thenReturn(1L);

        mockMvc.perform(get("/kanji").param("reading", "gyo"))
                .andExpect(status().isOk())
                .andExpect(view().name("kanji/list"))
                .andExpect(model().attribute("reading", "gyo"))
                .andExpect(content().string(containsString("Tìm thấy <strong>1</strong> kết quả")))
                .andExpect(content().string(containsString("魚")))
                .andExpect(content().string(containsString("NGƯ")));

        verify(kanjiService).search(any(), any(), eq("gyo"), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("F18: Lọc bài học Kanji có phân nhóm cha con qua topicGroups")
    void kanjiList_rendersHierarchicalOptgroups() throws Exception {
        TopicGroupDto group = new TopicGroupDto("Kanji N5", List.of(testTopicChild));
        when(kanjiService.getKanjiTopicGroups()).thenReturn(List.of(group));
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testKanjiFish)));
        when(kanjiService.countTotal()).thenReturn(1L);

        mockMvc.perform(get("/kanji"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<optgroup label=\"Kanji N5\">")))
                .andExpect(content().string(containsString("Bài 1")));
    }

    @Test
    @DisplayName("F24: Lọc chỉ Kanji có từ cần nhớ (hasWordsOnly=true)")
    void kanjiList_withHasWordsOnly_passesFlagToService() throws Exception {
        when(kanjiService.search(any(), any(), any(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testKanjiFish)));
        when(kanjiService.countTotal()).thenReturn(1L);

        mockMvc.perform(get("/kanji").param("hasWordsOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasWordsOnly", true));

        verify(kanjiService).search(any(), any(), any(), eq(true), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE_LINE", "CHARACTER", "HAN_VIET"})
    @DisplayName("F24: Sắp xếp Kanji theo whitelist hợp lệ")
    void kanjiList_validSortOptions_accepted(String sortParam) throws Exception {
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testKanjiFish)));
        when(kanjiService.countTotal()).thenReturn(1L);

        mockMvc.perform(get("/kanji").param("sort", sortParam))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", sortParam));
    }

    @Test
    @DisplayName("F24: Sắp xếp Kanji với tham số lạ không gây lỗi 500 và fallback SOURCE_LINE")
    void kanjiList_invalidSort_fallsBackToSourceLine() throws Exception {
        when(kanjiService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testKanjiFish)));
        when(kanjiService.countTotal()).thenReturn(1L);

        mockMvc.perform(get("/kanji").param("sort", "DROPTABLE;--"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "SOURCE_LINE"));
    }

    // =========================================================================
    // 2. Vocabulary Full View & Quick Filters (F21)
    // =========================================================================

    @Test
    @DisplayName("F21: Chế độ xem thu gọn (mặc định COMPACT) không hiển thị cột Hán Việt")
    void vocabList_compactView_doesNotShowHanVietColumn() throws Exception {
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testVocabFish)));

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "COMPACT"))
                .andExpect(content().string(containsString("Tìm thấy <strong>1</strong> kết quả")))
                .andExpect(content().string(not(containsString("<th class=\"u-width-16pct\">Hán Việt</th>"))));
    }

    @Test
    @DisplayName("F21: Chế độ xem 'Bảng đầy đủ' (view=FULL) hiển thị cột Hán Việt và link Kanji")
    void vocabList_fullView_rendersHanVietColumnAndKanjiBadge() throws Exception {
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        when(vocabularyService.search(any(), any(), eq(VocabQuickFilter.ALL), eq(VocabViewMode.FULL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testVocabFish)));

        mockMvc.perform(get("/vocabulary").param("view", "FULL"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "FULL"))
                .andExpect(content().string(containsString("Hán Việt")))
                .andExpect(content().string(containsString("constituent-kanji-wrap")))
                .andExpect(content().string(containsString("NGƯ")))
                .andExpect(content().string(containsString("/kanji/101")));

        verify(vocabularyService).search(any(), any(), eq(VocabQuickFilter.ALL), eq(VocabViewMode.FULL), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "HAS_KANJI", "NO_READING", "NO_MEANING"})
    @DisplayName("F21: Bộ lọc nhanh từ vựng với các tham số whitelist hợp lệ")
    void vocabList_validQuickFilters_passedToService(String filter) throws Exception {
        VocabQuickFilter expected = VocabQuickFilter.fromString(filter);
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        if (expected == VocabQuickFilter.ALL) {
            when(vocabularyService.search(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testVocabFish)));
        } else {
            when(vocabularyService.search(any(), any(), eq(expected), eq(VocabViewMode.COMPACT), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testVocabFish)));
        }

        mockMvc.perform(get("/vocabulary").param("filter", filter))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", filter));
    }

    @Test
    @DisplayName("F21: Bộ lọc nhanh từ vựng với tham số lạ fallback an toàn về ALL")
    void vocabList_invalidQuickFilter_fallsBackToAll() throws Exception {
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testVocabFish)));

        mockMvc.perform(get("/vocabulary").param("filter", "HACKER_FILTER"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "ALL"));
    }

    @Test
    @DisplayName("F21: Phân trang bảo toàn view và filter trên URL")
    void vocabList_paginationRetainsViewAndFilter() throws Exception {
        VocabularyRowDto item = testVocabFish;
        Page<VocabularyRowDto> secondPage = new PageImpl<>(List.of(item), org.springframework.data.domain.PageRequest.of(1, 1), 2);

        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        when(vocabularyService.search(any(), any(), eq(VocabQuickFilter.HAS_KANJI), eq(VocabViewMode.FULL), any(Pageable.class)))
                .thenReturn(secondPage);

        mockMvc.perform(get("/vocabulary")
                        .param("page", "1")
                        .param("size", "1")
                        .param("filter", "HAS_KANJI")
                        .param("view", "FULL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("filter=HAS_KANJI")))
                .andExpect(content().string(containsString("view=FULL")));
    }

    @Test
    @DisplayName("Bảo mật & Ổn định: Tham số độc hại, chuỗi 10000 ký tự, page âm không gây lỗi 500")
    void badInputs_doNotTrigger500OrInternalLeak() throws Exception {
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopicChild));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        String hugeString = "A".repeat(10000);

        mockMvc.perform(get("/vocabulary")
                        .param("keyword", hugeString)
                        .param("page", "-999")
                        .param("size", "-5")
                        .param("filter", "<script>alert(1)</script>")
                        .param("view", "'; DROP TABLE vocabulary; --"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("StackTrace"))));
    }
}

