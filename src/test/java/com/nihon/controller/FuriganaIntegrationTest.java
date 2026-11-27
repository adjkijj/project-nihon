package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.FuriganaHelper;
import com.nihon.service.PracticeService;
import com.nihon.service.StudyProgressService;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp cho tính năng F31: Bật/Tắt phiên âm Furigana.
 * Kiểm tra việc render thẻ ruby, không tạo ruby rỗng, bảo mật không lộ rt trong trang luyện tập,
 * và sự hiện diện của nút toggle Furigana cùng các tài nguyên CSS/JS.
 */
@WebMvcTest(controllers = {VocabularyController.class, PracticeController.class})
@Import({WebConfig.class, CsrfInterceptor.class, FuriganaHelper.class})
class FuriganaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private StudyProgressService studyProgressService;

    @Test
    @DisplayName("Template hiển thị thẻ ruby khi từ có reading, bóc tách phần ngoặc nguồn")
    void templateRendersRubyWhenReadingPresent() throws Exception {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("Chủ đề 1");

        // Từ có Kanji và có reading, japaneseText chứa ngoặc nguồn "私（わたし）"
        VocabularyRowDto vocabWithReading = VocabularyRowDto.builder()
                .id(100L)
                .japaneseText("私（わたし）")
                .reading("わたし")
                .romaji("watashi")
                .meaningVi("Tôi")
                .topics(List.of(topic))
                .allIds(List.of(100L))
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(topic));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vocabWithReading)));

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                // Phải chứa thẻ ruby với base text "私" và rt "わたし"
                .andExpect(content().string(containsString("<ruby>私<rt>わたし</rt></ruby>")))
                // Không được lặp lại phần ngoặc bên trong thẻ ruby
                .andExpect(content().string(not(containsString("<ruby>私（わたし）"))))
                // Chứa nút toggle Furigana trên thanh điều hướng
                .andExpect(content().string(containsString("id=\"furigana-toggle-btn\"")))
                // Chứa nạp CSS và JS điều khiển Furigana
                .andExpect(content().string(containsString("furigana.css")))
                .andExpect(content().string(containsString("furigana-toggle.js")));
    }

    @Test
    @DisplayName("Template không tạo thẻ ruby hay rt rỗng khi từ không có reading")
    void templateDoesNotRenderRubyWhenReadingMissing() throws Exception {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("Chủ đề 1");

        // Từ không có reading (reading = null)
        VocabularyRowDto vocabWithoutReading = VocabularyRowDto.builder()
                .id(101L)
                .japaneseText("1歳")
                .reading(null)
                .romaji("issai")
                .meaningVi("1 tuổi")
                .topics(List.of(topic))
                .allIds(List.of(101L))
                .build();

        when(topicService.findAllOrdered()).thenReturn(List.of(topic));
        when(vocabularyService.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vocabWithoutReading)));

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1歳")))
                // Tuyệt đối không tạo thẻ ruby rỗng
                .andExpect(content().string(not(containsString("<ruby>1歳"))))
                .andExpect(content().string(not(containsString("<rt></rt>"))));
    }

    @Test
    @DisplayName("Trang luyện tập hỏi cách đọc không được chứa thẻ rt của đáp án trong HTML")
    void practiceQuestionPageDoesNotContainRtTagForReading() throws Exception {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("1. Chào hỏi");
        when(topicService.findAllOrdered()).thenReturn(List.of(topic));

        PracticeQuestionDto questionDto = PracticeQuestionDto.builder()
                .vocabId(10L)
                .japaneseText("私（わたし）")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .hasKanji(true)
                .requireReading(true)
                .readingMissingInSource(false)
                .build();

        when(practiceService.getNextQuestion(any(), any())).thenReturn(Optional.of(questionDto));

        mockMvc.perform(get("/practice/question").param("topicId", "1"))
                .andExpect(status().isOk())
                // Từ hiển thị cho người học đã tách phần ngoặc để không lộ đáp án
                .andExpect(content().string(containsString("class=\"japanese-word\">私</div>")))
                // Tuyệt đối không chứa thẻ rt trong toàn bộ trang kiểm tra
                .andExpect(content().string(not(containsString("<rt>"))))
                .andExpect(content().string(not(containsString("</rt>"))))
                .andExpect(content().string(not(containsString("<rt>わたし</rt>"))))
                // Nút toggle Furigana vẫn có mặt trên navbar
                .andExpect(content().string(containsString("id=\"furigana-toggle-btn\"")));
    }
}

