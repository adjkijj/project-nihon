package com.nihon.service.grammar;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Tiện ích chuyển đổi văn bản giải thích thuần sang HTML an toàn (F30).
 * Quy ước:
 * - Luôn thoát mã HTML (HTML escape) toàn bộ đầu vào trước khi dựng để ngăn chặn triệt để XSS.
 * - Các dòng bắt đầu bằng "- " được chuyển thành danh sách gạch đầu dòng (<ul><li>...</li></ul>).
 * - Các dòng văn bản thường được bọc trong thẻ đoạn văn (<p>...</p>).
 * - Không cho phép nhập mã HTML, không Markdown, không chèn siêu liên kết ngoài.
 */
public final class GrammarTextRenderer {

    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*-\\s+(.*)$");

    private GrammarTextRenderer() {
        // Ngăn khởi tạo tiện ích tĩnh
    }

    /**
     * Dựng văn bản giải thích thành mã HTML an toàn.
     *
     * @param rawText văn bản thô do người dùng nhập
     * @return chuỗi HTML an toàn đã được escape và định dạng
     */
    public static String renderExplanation(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // Bước 1: HTML escape các ký tự điều khiển HTML nguy hiểm (&, <, >, ", ')
        // Giữ nguyên các ký tự có dấu tiếng Việt và chữ tiếng Nhật UTF-8
        String escaped = escapeHtmlChars(rawText);

        // Bước 2: Tách dòng và dựng cấu trúc danh sách / đoạn văn
        String[] lines = escaped.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        boolean inList = false;

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                continue;
            }

            var matcher = BULLET_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                if (!inList) {
                    sb.append("<ul>");
                    inList = true;
                }
                String itemContent = matcher.group(1).trim();
                sb.append("<li>").append(itemContent).append("</li>");
            } else {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append("<p>").append(trimmed).append("</p>");
            }
        }

        if (inList) {
            sb.append("</ul>");
        }

        return sb.toString();
    }

    private static String escapeHtmlChars(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

