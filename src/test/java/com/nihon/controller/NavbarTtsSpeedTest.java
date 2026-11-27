package com.nihon.controller;

import com.nihon.service.SpeechHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tính năng Cài đặt tốc độ phát âm tiếng Nhật trên thanh điều hướng (navbar):
 * - Thẻ select xuất hiện trên navbar với ba mức: 1.0x, 0.85x, 0.75x. Mặc định 0.9.
 * - nihon-speech.js đọc localStorage nihon_tts_speed, kiểm tra giá trị nằm trong danh sách cho phép, rồi gán vào utterance.rate.
 * - Mọi nút phát âm hiện có dùng chung cài đặt này.
 * - Giá trị lạ hoặc ngoài danh sách thì dùng mặc định 0.9.
 * - Tuân thủ CSP: không có script inline, không có onclick.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NavbarTtsSpeedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. Thanh điều hướng navbar chứa select điều chỉnh tốc độ phát âm với 3 mức 1.0x, 0.85x, 0.75x")
    void testNavbarContainsTtsSpeedSelectWithThreeLevels() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"tts-speed-select\"")))
                .andExpect(content().string(containsString("value=\"1.0\">1.0x</option>")))
                .andExpect(content().string(containsString("value=\"0.85\">0.85x</option>")))
                .andExpect(content().string(containsString("value=\"0.75\">0.75x</option>")));
    }

    @Test
    @DisplayName("2. Tệp nihon-speech.js đọc localStorage nihon_tts_speed, kiểm tra danh sách hợp lệ và gán vào utterance.rate")
    void testSpeechScriptReadsLocalStorageAndValidatesAllowedSpeeds() throws IOException {
        Path scriptPath = Paths.get("src/main/resources/static/js/nihon-speech.js");
        assertThat(Files.exists(scriptPath)).isTrue();

        String js = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(js).contains("nihon_tts_speed");
        assertThat(js).contains("DEFAULT_RATE = 0.9");
        assertThat(js).contains("utterance.rate = getTtsSpeed()");
        assertThat(js).contains("ALLOWED_SPEEDS");
        assertThat(js).contains("1.0");
        assertThat(js).contains("0.85");
        assertThat(js).contains("0.75");
        assertThat(js).contains("try");
        assertThat(js).contains("catch");
    }

    @Test
    @DisplayName("3. Kiểm tra kiểm định giá trị tốc độ: giá trị lạ hoặc ngoài danh sách thì dùng mặc định 0.9")
    void testUnusualOrOutsideSpeedValuesUseDefault() {
        // Giá trị hợp lệ trong danh sách cho phép
        assertThat(SpeechHelper.resolveTtsSpeed("1.0")).isEqualTo(1.0);
        assertThat(SpeechHelper.resolveTtsSpeed("1")).isEqualTo(1.0);
        assertThat(SpeechHelper.resolveTtsSpeed("0.85")).isEqualTo(0.85);
        assertThat(SpeechHelper.resolveTtsSpeed("0.75")).isEqualTo(0.75);

        // Giá trị lạ hoặc ngoài danh sách phải trả về mặc định 0.9
        assertThat(SpeechHelper.resolveTtsSpeed(null)).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("   ")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("invalid_speed")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("abc")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("2.0")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("0.5")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("-1")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("999")).isEqualTo(0.9);
        assertThat(SpeechHelper.resolveTtsSpeed("NaN")).isEqualTo(0.9);
    }

    @Test
    @DisplayName("4. Fragment navbar và speech không chứa script inline hoặc onclick tuân thủ CSP")
    void testFragmentsContainNoInlineScriptOrOnclick() throws IOException {
        for (String frag : new String[]{"fragments/navbar.html", "fragments/speech.html"}) {
            Path path = Paths.get("src/main/resources/templates/" + frag);
            String content = Files.readString(path, StandardCharsets.UTF_8)
                    .replaceAll("(?s)<!--.*?-->", "");

            assertThat(content)
                    .withFailMessage("Fragment %s chứa script inline", frag)
                    .doesNotMatch("(?s).*<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>.*");

            assertThat(content)
                    .withFailMessage("Fragment %s chứa onclick", frag)
                    .doesNotContain("onclick=");
        }
    }
}

