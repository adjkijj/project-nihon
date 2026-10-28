package com.nihon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bảo đảm template tương thích với Content-Security-Policy nghiêm ngặt (không 'unsafe-inline'):
 * không được có thẻ style/script nhúng, thuộc tính style="...", th:style hay trình xử lý sự kiện on*="...".
 */
class TemplateCspComplianceTest {

    private static final Path TEMPLATES = Paths.get("src/main/resources/templates");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("<style[\\s>]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<script(?![^>]*\\ssrc=)(?![^>]*\\sth:src=)[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sstyle\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\sth:style(append|prepend)?\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\s(th:)?on[a-z]+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE)
    );

    @Test
    @DisplayName("Template không chứa CSS/JS nhúng (phù hợp CSP không có 'unsafe-inline')")
    void templatesContainNoInlineStylesOrScripts() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            violations = files.filter(p -> p.toString().endsWith(".html"))
                    .flatMap(p -> findViolations(p).stream())
                    .collect(Collectors.toList());
        }
        assertTrue(violations.isEmpty(), "Vi phạm CSP trong template:\n" + String.join("\n", violations));
    }

    private static List<String> findViolations(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            // bỏ qua chú thích HTML
            final String content = raw.replaceAll("(?s)<!--.*?-->", "");
            String name = TEMPLATES.relativize(file).toString();
            return FORBIDDEN.stream()
                    .filter(p -> p.matcher(content).find())
                    .map(p -> name + " khớp " + p.pattern())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
