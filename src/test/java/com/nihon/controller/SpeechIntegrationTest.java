package com.nihon.controller;

import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.PracticeAnswerForm;
import com.nihon.dto.PracticeQuestionDto;
import com.nihon.dto.PracticeResultDto;
import com.nihon.dto.VocabularyDetailDto;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.PracticeService;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {VocabularyController.class, PracticeController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class SpeechIntegrationTest {

    private static final String CSRF_TOKEN = "speech-test-csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private com.nihon.service.StudyProgressService studyProgressService;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        testTopic = new Topic();
        testTopic.setId(1L);
        testTopic.setName("Chủ đề kiểm tra");
        when(topicService.findAllOrdered()).thenReturn(List.of(testTopic));
    }

    @Test
    @DisplayName("2. Template vocabulary/list: Nút loa có data-speech-text và được HTML escape đúng chuẩn khi có dấu nháy và dấu nhỏ hơn")
    void vocabularyList_speakerButtonEscapesSpecialCharacters() throws Exception {
        // Từ vựng có reading chứa dấu nháy kép và dấu nhỏ hơn: "わたし"<test>
        VocabularyRowDto rowDto = VocabularyRowDto.builder()
                .id(100L)
                .japaneseText("私")
                .reading("\"わたし\"<test>")
                .meaningVi("Tôi")
                .hasKanji(true)
                .topics(List.of(testTopic))
                .allIds(List.of(100L))
                .build();

        Page<VocabularyRowDto> page = new PageImpl<>(List.of(rowDto));
        when(vocabularyService.search(any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-speaker")))
                // Ký tự " và < phải được escape an toàn trong thuộc tính HTML
                .andExpect(content().string(containsString("data-speech-text=\"&quot;わたし&quot;&lt;test&gt;\"")));
    }

    @Test
    @DisplayName("3. Trang câu hỏi hỏi cách đọc KHÔNG chứa nút loa và KHÔNG chứa data-speech-text của từ đang hỏi")
    void practiceQuestion_doesNotExposeSpeechTextOrSpeakerButton() throws Exception {
        PracticeQuestionDto questionDto = PracticeQuestionDto.builder()
                .vocabId(10L)
                .japaneseText("私（わたし）")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .hasKanji(true)
                .requireReading(true)
                .readingMissingInSource(false)
                .build();

        when(practiceService.getNextQuestion(eq(1L), any())).thenReturn(Optional.of(questionDto));

        mockMvc.perform(get("/practice/question").param("topicId", "1"))
                .andExpect(status().isOk())
                // Tuyệt đối không được chứa nút loa hoặc data-speech-text làm lộ đáp án cách đọc
                .andExpect(content().string(not(containsString("btn-speaker"))))
                .andExpect(content().string(not(containsString("data-speech-text"))));
    }

    @Test
    @DisplayName("Trang kết quả luyện tập hiển thị nút loa để người học nghe phát âm đáp án chuẩn")
    void practiceResult_displaysSpeakerButton() throws Exception {
        PracticeAnswerForm form = new PracticeAnswerForm();
        form.setVocabId(10L);
        form.setTopicId(1L);
        form.setUserReading("わたし");
        form.setUserMeaning("Tôi");

        PracticeResultDto resultDto = PracticeResultDto.builder()
                .vocabId(10L)
                .japaneseText("私")
                .topicName("1. Chào hỏi")
                .topicId(1L)
                .userReading("わたし")
                .userMeaning("Tôi")
                .readingCorrect(true)
                .meaningCorrect(true)
                .overallCorrect(true)
                .expectedReading("わたし")
                .expectedMeaning("Tôi")
                .feedbackMessage("Chính xác hoàn toàn!")
                .build();

        when(practiceService.checkAnswer(any(PracticeAnswerForm.class))).thenReturn(resultDto);

        mockMvc.perform(post("/practice/check")
                        .param("_csrf", CSRF_TOKEN)
                        .sessionAttr(CsrfInterceptor.CSRF_SESSION_ATTR, CSRF_TOKEN)
                        .param("vocabId", "10")
                        .param("topicId", "1")
                        .param("userReading", "わたし")
                        .param("userMeaning", "Tôi"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-speaker")))
                .andExpect(content().string(containsString("data-speech-text=\"わたし\"")));
    }

    @Test
    @DisplayName("Trang chi tiết từ vựng vocabulary/detail hiển thị nút loa phát âm")
    void vocabularyDetail_displaysSpeakerButton() throws Exception {
        VocabularyDetailDto detailDto = VocabularyDetailDto.builder()
                .id(1L)
                .japaneseText("先生")
                .reading("せんせい")
                .romaji("sensei")
                .meaningVi("Giáo viên")
                .hasKanji(true)
                .topics(List.of(testTopic))
                .allIds(List.of(1L))
                .kanjis(Collections.emptyList())
                .build();

        when(vocabularyService.findDetailById(1L)).thenReturn(Optional.of(detailDto));

        mockMvc.perform(get("/vocabulary/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-speaker")))
                .andExpect(content().string(containsString("data-speech-text=\"せんせい\"")));
    }
}

