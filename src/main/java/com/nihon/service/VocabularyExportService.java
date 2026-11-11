package com.nihon.service;

import com.nihon.dto.ExportTextFormat;
import com.nihon.dto.VocabularyRowDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dịch vụ xử lý xuất danh sách từ vựng thành tệp văn bản (.txt) cho Quizlet và Anki.
 */
@Service
public class VocabularyExportService {

    public static final int MAX_EXPORT_LIMIT = 5000;
    private static final String LINE_SEPARATOR = "\r\n";
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Xuất danh sách từ vựng thành mảng byte với định dạng được chọn và có gắn BOM UTF-8.
     *
     * @param rows Danh sách từ vựng đã lọc
     * @param format Định dạng xuất (TAB hoặc COLON)
     * @return Mảng byte chứa nội dung tệp văn bản kèm BOM UTF-8
     */
    public byte[] exportToText(List<VocabularyRowDto> rows, ExportTextFormat format) {
        ExportTextFormat actualFormat = (format != null) ? format : ExportTextFormat.TAB;
        List<String> lines = new ArrayList<>();

        if (rows != null) {
            for (VocabularyRowDto row : rows) {
                if (row == null) {
                    continue;
                }
                String line = formatLine(row, actualFormat);
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            contentBuilder.append(lines.get(i));
            contentBuilder.append(LINE_SEPARATOR);
        }

        byte[] contentBytes = contentBuilder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, result, UTF8_BOM.length, contentBytes.length);

        return result;
    }

    /**
     * Định dạng một dòng từ vựng theo cấu hình format đã chọn.
     *
     * @param row Dữ liệu hàng từ vựng
     * @param format Định dạng xuất
     * @return Chuỗi nội dung dòng đã định dạng, hoặc null nếu dòng rỗng
     */
    public String formatLine(VocabularyRowDto row, ExportTextFormat format) {
        String baseJp = FuriganaHelper.extractBaseText(row.getJapaneseText());
        String cleanJp = cleanField(baseJp);
        String cleanReading = cleanField(row.getReading());
        String cleanMeaning = cleanField(row.getMeaningVi());

        // Nếu tất cả các trường đều trống sau khi làm sạch, coi như dòng rỗng và bỏ qua
        if (cleanJp.isEmpty() && cleanReading.isEmpty() && cleanMeaning.isEmpty()) {
            return null;
        }

        if (format == ExportTextFormat.COLON) {
            StringBuilder sb = new StringBuilder();
            sb.append(cleanJp);
            if (!cleanReading.isEmpty()) {
                if (!cleanJp.isEmpty()) {
                    sb.append(" ");
                }
                sb.append("(").append(cleanReading).append(")");
            }
            sb.append(" : ").append(cleanMeaning);
            return sb.toString();
        } else {
            // Mặc định định dạng TAB: Tiếng Nhật \t Cách đọc \t Nghĩa
            return cleanJp + "\t" + cleanReading + "\t" + cleanMeaning;
        }
    }

    /**
     * Làm sạch giá trị cột: thay tab, xuống dòng và ký tự điều khiển bằng khoảng trắng,
     * sau đó rút gọn khoảng trắng thừa để không làm vỡ cấu trúc cột.
     *
     * @param value Giá trị chuỗi đầu vào
     * @return Chuỗi đã được làm sạch an toàn
     */
    public static String cleanField(String value) {
        if (value == null) {
            return "";
        }
        // Thay thế tab, \r, \n và các ký tự điều khiển Unicode bằng khoảng trắng
        String cleaned = value.replaceAll("[\\t\\r\\n\\p{Cntrl}]", " ");
        // Rút gọn các khoảng trắng liền nhau thành 1 khoảng trắng và trim
        return cleaned.replaceAll(" +", " ").trim();
    }
}

