package com.nihon.service.grammar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarTextRendererTest {

    @Test
    @DisplayName("Chuỗi null hoặc rỗng trả về chuỗi rỗng")
    void testNullOrEmptyReturnsEmpty() {
        assertThat(GrammarTextRenderer.renderExplanation(null)).isEmpty();
        assertThat(GrammarTextRenderer.renderExplanation("")).isEmpty();
        assertThat(GrammarTextRenderer.renderExplanation("   ")).isEmpty();
    }

    @Test
    @DisplayName("Chặn HTML: Đầu vào có <script> được escape an toàn khi hiện")
    void testHtmlEscapeScriptTag() {
        String input = "Cấu trúc này có thẻ <script>alert('xss')</script> và <b>chữ đậm</b>.";
        String html = GrammarTextRenderer.renderExplanation(input);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("</script>");
        assertThat(html).doesNotContain("<b>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).contains("&lt;/script&gt;");
        assertThat(html).contains("&lt;b&gt;");
        assertThat(html).startsWith("<p>");
        assertThat(html).endsWith("</p>");
    }

    @Test
    @DisplayName("Dựng danh sách '- ': Mỗi dòng bắt đầu bằng '- ' được dựng thành <li> bọc trong <ul>")
    void testBulletListRendering() {
        String input = """
                Cấu trúc diễn đạt lý do:
                - Thường dùng trong văn nói hàng ngày
                - Đi kèm với thể thường hoặc thể lịch sự
                Chú ý không dùng trong ngữ cảnh quá trang trọng.
                """;

        String html = GrammarTextRenderer.renderExplanation(input);

        assertThat(html).contains("<p>Cấu trúc diễn đạt lý do:</p>");
        assertThat(html).contains("<ul><li>Thường dùng trong văn nói hàng ngày</li><li>Đi kèm với thể thường hoặc thể lịch sự</li></ul>");
        assertThat(html).contains("<p>Chú ý không dùng trong ngữ cảnh quá trang trọng.</p>");
    }

    @Test
    @DisplayName("Nhiều khối gạch đầu dòng ngắt quãng bởi đoạn văn")
    void testMultipleListBlocks() {
        String input = """
                Đặc điểm 1:
                - Mục A
                - Mục B
                Đặc điểm 2:
                - Mục C
                - Mục D
                """;

        String html = GrammarTextRenderer.renderExplanation(input);

        assertThat(html).contains("<p>Đặc điểm 1:</p>");
        assertThat(html).contains("<ul><li>Mục A</li><li>Mục B</li></ul>");
        assertThat(html).contains("<p>Đặc điểm 2:</p>");
        assertThat(html).contains("<ul><li>Mục C</li><li>Mục D</li></ul>");
    }

    @Test
    @DisplayName("Xử lý an toàn các ký tự đặc biệt &, \", '")
    void testSpecialCharsEscaping() {
        String input = "- Tom & Jerry \"cartoon\" 'classic'";
        String html = GrammarTextRenderer.renderExplanation(input);

        assertThat(html).contains("<ul><li>");
        assertThat(html).contains("&amp;");
        assertThat(html).contains("&quot;");
        assertThat(html).contains("&#39;");
        assertThat(html).endsWith("</li></ul>");
    }
}

