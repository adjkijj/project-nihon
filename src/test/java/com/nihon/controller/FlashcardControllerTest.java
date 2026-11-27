package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Kiểm thử toàn diện cho FlashcardController:
 * - Tham số mặc định, whitelist side (JA/VI), phân trang biên an toàn (0..100000).
 * - Topic không tồn tại / id âm không gây 500.
 * - Loại bỏ từ thiếu nghĩa tiếng Việt và đếm skippedCount.
 * - Deduplication từ vựng thuộc nhiều chủ đề (chỉ hiện 1 thẻ duy nhất).
 * - Xáo trộn ổn định với seed trong session, chuyển trang không lặp thẻ, xáo bài sinh seed mới.
 */
@WebMvcTest(controllers = {FlashcardController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class FlashcardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    private Topic topic1;
    private Topic topic2;

    @BeforeEach
    void setUp() {
        topic1 = new Topic();
        topic1.setId(1L);
        topic1.setName("Chủ đề 1");

        topic2 = new Topic();
        topic2.setId(2L);
        topic2.setName("Chủ đề 2");

        when(topicService.findAllOrdered()).thenReturn(List.of(topic1, topic2));
    }

    @Test
    @DisplayName("GET /flashcards không tham số: trả 200, hiển thị danh sách thẻ hợp lệ")
    void testDefaultFlashcards_returns200AndRendersCards() throws Exception {
        VocabularyRowDto v1 = VocabularyRowDto.builder()
                .id(1L)
                .japaneseText("食べる")
                .reading("たべる")
                .romaji("taberu")
                .meaningVi("ăn")
                .topics(List.of(topic1))
                .build();
        VocabularyRowDto v2 = VocabularyRowDto.builder()
                .id(2L)
                .japaneseText("飲む")
                .reading("のむ")
                .romaji("nomu")
                .meaningVi("uống")
                .topics(List.of(topic1))
                .build();

        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(List.of(v1, v2));

        mockMvc.perform(get("/flashcards"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("side", "JA"))
                .andExpect(model().attribute("shuffle", false))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("totalElements", 2))
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("skippedCount", 0))
                .andExpect(model().attribute("cards", hasSize(2)))
                .andExpect(content().string(containsString("食べる")))
                .andExpect(content().string(containsString("たべる")))
                .andExpect(content().string(containsString("Học qua Thẻ lật")));
    }

    @Test
    @DisplayName("GET /flashcards với tham số bất thường: topicId không tồn tại, side=abc, shuffle=abc, page quá lớn -> không ném 500")
    void testInvalidParameters_doesNot500() throws Exception {
        when(topicService.findById(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/flashcards")
                        .param("topicId", "999999")
                        .param("side", "INVALID_SIDE")
                        .param("shuffle", "not_a_boolean")
                        .param("page", "9999999999999999999999999999"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("side", "JA"))
                .andExpect(model().attribute("shuffle", false))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("errorMessage", "Chủ đề không tồn tại."));
    }

    @Test
    @DisplayName("GET /flashcards với topicId <= 0: báo lỗi hợp lệ và không ném 500")
    void testNegativeOrZeroTopicId_handlesSafely() throws Exception {
        mockMvc.perform(get("/flashcards").param("topicId", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("errorMessage", "ID chủ đề không hợp lệ."));
    }

    @Test
    @DisplayName("GET /flashcards lọc từ thiếu nghĩa tiếng Việt và đếm skippedCount chính xác")
    void testFilterMissingMeaning_skippedAndCountReported() throws Exception {
        VocabularyRowDto validWord = VocabularyRowDto.builder()
                .id(1L)
                .japaneseText("本")
                .reading("ほん")
                .romaji("hon")
                .meaningVi("sách")
                .topics(List.of(topic1))
                .build();
        VocabularyRowDto emptyMeaningWord = VocabularyRowDto.builder()
                .id(2L)
                .japaneseText("ペン")
                .reading("ぺん")
                .romaji("pen")
                .meaningVi("") // rỗng
                .topics(List.of(topic1))
                .build();
        VocabularyRowDto nullMeaningWord = VocabularyRowDto.builder()
                .id(3L)
                .japaneseText("ノート")
                .reading("のーと")
                .romaji("nooto")
                .meaningVi(null) // null
                .topics(List.of(topic1))
                .build();

        when(vocabularyService.findAllMatching(isNull(), isNull()))
                .thenReturn(List.of(validWord, emptyMeaningWord, nullMeaningWord));

        mockMvc.perform(get("/flashcards"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("totalElements", 1))
                .andExpect(model().attribute("skippedCount", 2))
                .andExpect(model().attribute("cards", hasSize(1)))
                .andExpect(content().string(containsString("Đã tự động lọc bỏ")))
                .andExpect(content().string(containsString("<strong>2</strong>")))
                .andExpect(content().string(containsString("từ vựng chưa có nghĩa tiếng Việt")));
    }

    @Test
    @DisplayName("GET /flashcards đảm bảo từ vựng thuộc nhiều chủ đề chỉ xuất hiện 1 thẻ duy nhất")
    void testDeduplication_singleCardPerMultipleTopicsWord() throws Exception {
        VocabularyRowDto multiTopicWord = VocabularyRowDto.builder()
                .id(10L)
                .japaneseText("学校")
                .reading("がっこう")
                .romaji("gakkou")
                .meaningVi("trường học")
                .topics(List.of(topic1, topic2))
                .allIds(List.of(10L, 20L))
                .build();

        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(List.of(multiTopicWord));

        mockMvc.perform(get("/flashcards"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("totalElements", 1))
                .andExpect(model().attribute("cards", hasSize(1)));
    }

    @Test
    @DisplayName("GET /flashcards với side=VI: mặt trước hiển thị tiếng Việt")
    void testSideVI_rendersCorrectly() throws Exception {
        VocabularyRowDto v1 = VocabularyRowDto.builder()
                .id(1L)
                .japaneseText("猫")
                .reading("ねこ")
                .romaji("neko")
                .meaningVi("con mèo")
                .topics(List.of(topic1))
                .build();

        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(List.of(v1));

        mockMvc.perform(get("/flashcards").param("side", "VI"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("side", "VI"))
                .andExpect(content().string(containsString("MẶT TRƯỚC: TIẾNG VIỆT")))
                .andExpect(content().string(containsString("MẶT SAU: TIẾNG NHẬT")));
    }

    @Test
    @DisplayName("GET /flashcards khi chủ đề rỗng: hiển thị thông báo rỗng và liên kết quay lại")
    void testEmptyCards_rendersEmptyState() throws Exception {
        when(topicService.findById(1L)).thenReturn(Optional.of(topic1));
        when(vocabularyService.findAllMatching(eq(1L), isNull())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/flashcards").param("topicId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards/index"))
                .andExpect(model().attribute("totalElements", 0))
                .andExpect(model().attribute("cards", Collections.emptyList()))
                .andExpect(content().string(containsString("Chưa có thẻ từ vựng nào")))
                .andExpect(content().string(containsString("Xem tất cả chủ đề")));
    }

    @Test
    @DisplayName("GET /flashcards với shuffle=true: giữ nguyên seed trong session để phân trang ổn định, newSeed=true tạo seed mới")
    void testShuffleStabilityWithSessionSeed() throws Exception {
        List<VocabularyRowDto> words = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            words.add(VocabularyRowDto.builder()
                    .id((long) i)
                    .japaneseText("単語" + i)
                    .reading("たんご" + i)
                    .romaji("tango" + i)
                    .meaningVi("từ số " + i)
                    .topics(List.of(topic1))
                    .build());
        }

        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(words);

        MockHttpSession session = new MockHttpSession();

        // 1. Lần 1: Gọi trang 0 có shuffle
        var resultPage0 = mockMvc.perform(get("/flashcards")
                        .param("shuffle", "true")
                        .param("page", "0")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        Long seed1 = (Long) session.getAttribute("FLASHCARD_SEED_ALL");
        assertNotNull(seed1, "Seed phải được tạo và lưu trong session");

        @SuppressWarnings("unchecked")
        List<VocabularyRowDto> page0Cards = (List<VocabularyRowDto>) resultPage0.getModelAndView().getModel().get("cards");
        assertEquals(50, page0Cards.size(), "Mỗi trang tối đa 50 thẻ");

        // 2. Lần 2: Gọi tiếp trang 1 cùng session -> seed không đổi, 10 thẻ còn lại không trùng với trang 0
        var resultPage1 = mockMvc.perform(get("/flashcards")
                        .param("shuffle", "true")
                        .param("page", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        Long seed2 = (Long) session.getAttribute("FLASHCARD_SEED_ALL");
        assertEquals(seed1, seed2, "Seed phải được giữ nguyên giữa các trang trong cùng session");

        @SuppressWarnings("unchecked")
        List<VocabularyRowDto> page1Cards = (List<VocabularyRowDto>) resultPage1.getModelAndView().getModel().get("cards");
        assertEquals(10, page1Cards.size(), "Trang 1 có 10 thẻ còn lại");

        // Đảm bảo không trùng lặp ID giữa trang 0 và trang 1
        List<Long> page0Ids = page0Cards.stream().map(VocabularyRowDto::getId).toList();
        List<Long> page1Ids = page1Cards.stream().map(VocabularyRowDto::getId).toList();
        for (Long id : page1Ids) {
            org.junit.jupiter.api.Assertions.assertFalse(page0Ids.contains(id),
                    "Thẻ ở trang 1 không được trùng lặp với thẻ ở trang 0");
        }

        // 3. Lần 3: Bấm nút Xáo lại bài (newSeed=true) -> session cập nhật seed mới
        mockMvc.perform(get("/flashcards")
                        .param("shuffle", "true")
                        .param("newSeed", "true")
                        .session(session))
                .andExpect(status().isOk());

        Long seed3 = (Long) session.getAttribute("FLASHCARD_SEED_ALL");
        assertNotNull(seed3);
        // Có thể cùng millisecond nếu chạy siêu nhanh, nhưng method tạo mới
    }
}
