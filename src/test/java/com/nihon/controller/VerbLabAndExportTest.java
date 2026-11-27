package com.nihon.controller;

import com.nihon.config.ClientIpResolver;
import com.nihon.config.CsrfInterceptor;
import com.nihon.config.WebConfig;
import com.nihon.dto.verb.VerbLabResultDto;
import com.nihon.service.TopicService;
import com.nihon.service.VerbService;
import com.nihon.service.VocabularyExportRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {VerbController.class, GlobalExceptionHandler.class})
@Import({WebConfig.class, CsrfInterceptor.class})
class VerbLabAndExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerbService verbService;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private VocabularyExportRateLimiter exportRateLimiter;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");
        when(exportRateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("1. GET /verbs/lab: Trang ban đầu trả về 200 OK và view verbs/lab")
    void testLabInitialPage() throws Exception {
        when(verbService.processLabInput(null, "AUTO"))
                .thenReturn(VerbLabResultDto.builder().hasResult(false).build());

        mockMvc.perform(get("/verbs/lab"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/lab"))
                .andExpect(model().attributeExists("labResult"))
                .andExpect(content().string(containsString("Phòng biến đổi động từ")));
    }

    @Test
    @DisplayName("2. GET /verbs/lab: Kết quả biến đổi hiển thị bảng 5 thể và phát âm")
    void testLabSuccessfulTransformation() throws Exception {
        VerbLabResultDto result = VerbLabResultDto.builder()
                .input("のむ")
                .selectedGroup("1")
                .inferredGroup(1)
                .dictionaryForm("のむ")
                .masuForm("のみます")
                .kanjiText("飲む")
                .teForm("のんで")
                .taForm("のんだ")
                .naiForm("のまない")
                .kanjiDictionaryForm("飲む")
                .kanjiMasuForm("飲みます")
                .kanjiTeForm("飲んで")
                .kanjiTaForm("飲んだ")
                .kanjiNaiForm("飲まない")
                .hasResult(true)
                .build();

        when(verbService.processLabInput(eq("のむ"), eq("1"))).thenReturn(result);

        mockMvc.perform(get("/verbs/lab")
                        .param("input", "のむ")
                        .param("group", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/lab"))
                .andExpect(model().attribute("labResult", result))
                .andExpect(content().string(containsString("Bảng 5 thể")))
                .andExpect(content().string(containsString("のんで")))
                .andExpect(content().string(containsString("飲んで")));
    }

    @Test
    @DisplayName("3. GET /verbs/lab: Cảnh báo ambiguous khi để AUTO với động từ không phải suru/kuru")
    void testLabAmbiguousWarning() throws Exception {
        VerbLabResultDto ambiguous = VerbLabResultDto.ambiguous(
                "のむ", "AUTO",
                "Động từ 'のむ' cần được chỉ định rõ nhóm (Nhóm 1 hoặc Nhóm 2) để chia thể chính xác."
        );

        when(verbService.processLabInput(eq("のむ"), eq("AUTO"))).thenReturn(ambiguous);

        mockMvc.perform(get("/verbs/lab")
                        .param("input", "のむ")
                        .param("group", "AUTO"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/lab"))
                .andExpect(content().string(containsString("Cần xác định nhóm động từ")))
                .andExpect(content().string(containsString("cần được chỉ định rõ nhóm")));
    }

    @Test
    @DisplayName("4. GET /verbs/lab: Đầu vào lạ, XSS script, chuỗi Latin không bị lỗi 500 và escape an toàn")
    void testLabMaliciousInputSafeHandling() throws Exception {
        String xssPayload = "<script>alert('xss')</script>";
        VerbLabResultDto errorResult = VerbLabResultDto.error(
                xssPayload, "AUTO", "Vui lòng nhập động từ tiếng Nhật hợp lệ bằng chữ Hiragana hoặc Kanji."
        );

        when(verbService.processLabInput(eq(xssPayload), eq("AUTO"))).thenReturn(errorResult);

        mockMvc.perform(get("/verbs/lab")
                        .param("input", xssPayload)
                        .param("group", "AUTO"))
                .andExpect(status().isOk())
                .andExpect(view().name("verbs/lab"))
                // Script tag phải được HTML entity escape, không xuất hiện dạng mã thực thi
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(")));
    }

    @Test
    @DisplayName("5. GET /verbs/export-text: Xuất file Quizlet định dạng Tab, UTF-8 BOM, đúng Header")
    void testExportQuizletText() throws Exception {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String body = "nomimasu\tnomi-masu\r\n";
        byte[] fullContent = new byte[bom.length + body.getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(bom, 0, fullContent, 0, bom.length);
        System.arraycopy(body.getBytes(StandardCharsets.UTF_8), 0, fullContent, bom.length, body.getBytes(StandardCharsets.UTF_8).length);

        when(verbService.exportQuizletText(any(), any(), any())).thenReturn(fullContent);

        mockMvc.perform(get("/verbs/export-text")
                        .param("form", "TE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"nihon-verbs-te-")))
                .andExpect(content().bytes(fullContent));
    }

    @Test
    @DisplayName("6. GET /verbs/export-text: Bị chặn khi vượt quá giới hạn tần suất (Rate Limiting)")
    void testExportQuizletRateLimit() throws Exception {
        when(exportRateLimiter.tryAcquire(anyString())).thenReturn(false);

        mockMvc.perform(get("/verbs/export-text")
                        .param("form", "TE"))
                .andExpect(status().isTooManyRequests());
    }
}
