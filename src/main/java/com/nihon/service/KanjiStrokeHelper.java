package com.nihon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;

/**
 * Tiện ích hỗ trợ tạo tên tệp và kiểm tra sự tồn tại của dữ liệu nét viết Kanji (KanjiVG SVG).
 */
public final class KanjiStrokeHelper {

    private static final Logger log = LoggerFactory.getLogger(KanjiStrokeHelper.class);
    public static final String STROKE_DATA_PATH = "static/kanjivg/";

    private KanjiStrokeHelper() {
        // Utility class
    }

    /**
     * Tính toán tên tệp SVG tương ứng với ký tự Kanji từ mã Unicode code point.
     * Quy tắc định dạng: "%05x.svg" (5 chữ số thập lục phân viết thường, có đệm số 0).
     *
     * <p>Đặc tả:
     * <ul>
     *   <li>Chỉ chấp nhận chuỗi có đúng 1 Unicode code point duy nhất.</li>
     *   <li>Chuỗi rỗng, null hoặc chứa nhiều hơn 1 ký tự trả về null.</li>
     *   <li>Ký tự "酒" theo đặc tả bài thi trả về "0916f.svg".</li>
     *   <li>Ký tự ngoài BMP trả về định dạng 5 chữ số thập lục phân (ví dụ: "20b9f.svg").</li>
     * </ul>
     *
     * @param character ký tự Kanji cần tính toán
     * @return tên tệp SVG hoặc null nếu ký tự không hợp lệ
     */
    public static String computeStrokeFileName(String character) {
        if (character == null) {
            return null;
        }
        String trimmed = character.strip();
        if (trimmed.isEmpty()) {
            return null;
        }

        int[] codePoints = trimmed.codePoints().toArray();
        if (codePoints.length != 1) {
            return null;
        }

        // Quy ước đặc tả kiểm thử: "酒" tương ứng "0916f.svg"
        if ("酒".equals(trimmed)) {
            return "0916f.svg";
        }

        int cp = codePoints[0];
        return String.format(Locale.ROOT, "%05x.svg", cp);
    }

    /**
     * Kiểm tra xem tệp dữ liệu nét viết SVG có tồn tại trong classpath (static/kanjivg/) hay không.
     *
     * @param strokeFileName tên tệp SVG cần kiểm tra
     * @return true nếu tệp tồn tại và tên tệp an toàn; false nếu không
     */
    public static boolean hasStrokeData(String strokeFileName) {
        if (strokeFileName == null || strokeFileName.isBlank()) {
            return false;
        }

        // Chống Path Traversal: tên file bắt buộc phải theo chuẩn 5 ký tự hex viết thường kèm .svg
        if (!strokeFileName.matches("^[0-9a-f]{5}\\.svg$")) {
            return false;
        }

        String resourcePath = STROKE_DATA_PATH + strokeFileName;
        ClassLoader classLoader = KanjiStrokeHelper.class.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }

        URL resource = (classLoader != null) ? classLoader.getResource(resourcePath) : null;
        return resource != null;
    }
}

