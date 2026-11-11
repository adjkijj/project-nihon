package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.VocabularyRowDto;
import com.nihon.entity.Topic;
import com.nihon.service.TopicService;
import com.nihon.service.VocabularyExportRateLimiter;
import com.nihon.service.VocabularyExportService;
import com.nihon.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {VocabularyController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class, VocabularyExportService.class, VocabularyExportRateLimiter.class, ClientIpResolver.class})
class VocabularyExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VocabularyExportRateLimiter rateLimiter;

    @MockitoBean
    private VocabularyService vocabularyService;

    @MockitoBean
    private TopicService topicService;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
    }

    @Test
    @DisplayName("1. Xuất theo topicId trả đúng số dòng và đúng nội dung")
    void exportText_byTopicId_returnsCorrectRowsAndContent() throws Exception {
        Topic topic = new Topic();
        topic.setId(10L);
        topic.setName("Chủ đề N5");
        when(topicService.findById(10L)).thenReturn(Optional.of(topic));

        List<VocabularyRowDto> mockRows = List.of(
                VocabularyRowDto.builder().japaneseText("私").reading("わたし").meaningVi("Tôi").build(),
                VocabularyRowDto.builder().japaneseText("猫").reading("ねこ").meaningVi("Con mèo").build()
        );
        when(vocabularyService.findAllMatching(eq(10L), isNull())).thenReturn(mockRows);

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text")
                        .param("topicId", "10")
                        .param("format", "TAB"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(3);
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo("私\tわたし\tTôi");
        assertThat(lines[1]).isEqualTo("猫\tねこ\tCon mèo");
    }

    @Test
    @DisplayName("2. Dữ liệu có tab hoặc xuống dòng được làm sạch")
    void exportText_sanitizesTabsAndNewlines() throws Exception {
        List<VocabularyRowDto> mockRows = List.of(
                VocabularyRowDto.builder()
                        .japaneseText("本\t\n")
                        .reading("\rほん\t")
                        .meaningVi("Quyển\tsách\r\n")
                        .build()
        );
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(mockRows);

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text")
                        .param("format", "TAB"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("本\tほん\tQuyển sách");
    }

    @Test
    @DisplayName("3. format=abc dùng TAB")
    void exportText_formatAbc_fallsBackToTab() throws Exception {
        List<VocabularyRowDto> mockRows = List.of(
                VocabularyRowDto.builder().japaneseText("山").reading("やま").meaningVi("Núi").build()
        );
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(mockRows);

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text")
                        .param("format", "abc"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertThat(text).startsWith("山\tやま\tNúi");
    }

    @Test
    @DisplayName("Định dạng COLON hoạt động chuẩn: tiếng Nhật (cách đọc) : nghĩa")
    void exportText_formatColon() throws Exception {
        List<VocabularyRowDto> mockRows = List.of(
                VocabularyRowDto.builder().japaneseText("川").reading("かわ").meaningVi("Sông").build()
        );
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(mockRows);

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text")
                        .param("format", "COLON"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertThat(text).startsWith("川 (かわ) : Sông");
    }

    @Test
    @DisplayName("4. topicId không tồn tại trả tệp rỗng hoặc thông báo, không 500")
    void exportText_nonExistentTopicId_returnsEmptyFileNot500() throws Exception {
        when(topicService.findById(999999L)).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text")
                        .param("topicId", "999999"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // Có BOM (3 bytes) và không có dòng từ vựng nào
        assertThat(body).hasSize(3);
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    @DisplayName("5. Header đúng, Cache-Control no-store, Tên file theo mẫu cố định không phụ thuộc input")
    void exportText_headersAndFilenameNotDependentOnInput() throws Exception {
        when(vocabularyService.findAllMatching(isNull(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/vocabulary/export-text")
                        .param("keyword", "../../malicious/path")
                        .param("format", "TAB"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain; charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesRegex("attachment; filename=\"nihon-vocabulary-\\d{8}-\\d{4}\\.txt\"")));
    }

    @Test
    @DisplayName("6. Phản hồi xuất tệp luôn có BOM UTF-8 ở đầu")
    void exportText_containsUtf8Bom() throws Exception {
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(List.of(
                VocabularyRowDto.builder().japaneseText("桜").reading("さくら").meaningVi("Hoa anh đào").build()
        ));

        MvcResult result = mockMvc.perform(get("/vocabulary/export-text"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThanOrEqualTo(3);
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    @DisplayName("7. Vượt giới hạn 5000 dòng xử lý trả lỗi HTTP 400 Bad Request kèm thông báo")
    void exportText_exceedsMaxLimit_returns400BadRequest() throws Exception {
        List<VocabularyRowDto> hugeList = new ArrayList<>(5001);
        for (int i = 0; i < 5001; i++) {
            hugeList.add(VocabularyRowDto.builder().japaneseText("単語" + i).reading("たんご").meaningVi("Từ " + i).build());
        }
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(hugeList);

        mockMvc.perform(get("/vocabulary/export-text"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "text/plain; charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("5000 dòng")));
    }

    @Test
    @DisplayName("Giới hạn tần suất 20 lần/phút theo IP: lần thứ 21 trả 429 Too Many Requests")
    void exportText_rateLimiting_blocksAt21stRequest() throws Exception {
        when(vocabularyService.findAllMatching(isNull(), isNull())).thenReturn(Collections.emptyList());

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/vocabulary/export-text")
                            .with(request -> {
                                request.setRemoteAddr("192.168.1.100");
                                return request;
                            }))
                    .andExpect(status().isOk());
        }

        // Lần thứ 21 từ cùng IP
        mockMvc.perform(get("/vocabulary/export-text")
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.100");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests());
    }
}

