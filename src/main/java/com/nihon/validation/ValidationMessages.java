package com.nihon.validation;

/**
 * Tập hợp các hằng số thông báo lỗi tiếng Việt rõ ràng kèm ví dụ hợp lệ cho các annotation kiểm tra dữ liệu.
 */
public final class ValidationMessages {

    public static final String NO_CONTROL_CHARS =
            "Văn bản không được chứa ký tự điều khiển hoặc dấu xuống dòng, ví dụ: Bài 1 - Chào hỏi.";

    public static final String JAPANESE_TEXT =
            "Tiếng Nhật phải có ít nhất một ký tự Hiragana, Katakana hoặc chữ Hán, ví dụ: 私（わたし）.";

    public static final String KANA_READING =
            "Cách đọc chỉ gồm chữ Hiragana hoặc Katakana, ví dụ: わたし.";

    public static final String ONYOMI_READING =
            "Âm On chỉ gồm chữ Katakana hoặc Hiragana, dấu trường âm (ー), dấu phẩy (、) hoặc gạch ngang (-), ví dụ: スイ、コウ.";

    public static final String ROMAJI_TEXT =
            "Romaji chỉ gồm chữ Latin (a-z, A-Z) và nguyên âm có dấu trường âm (ā, ī, ū, ē, ō), ví dụ: watashi.";

    public static final String VIETNAMESE_TEXT =
            "Văn bản tiếng Việt chỉ gồm chữ cái tiếng Việt, số và dấu câu thông thường (không chứa '<' hoặc '>'), ví dụ: Xin chào.";

    public static final String HAN_VIET_TEXT =
            "Âm Hán Việt chỉ gồm chữ in hoa tiếng Việt, khoảng trắng và dấu phẩy, ví dụ: THỦY, HỎA.";

    public static final String SINGLE_KANJI =
            "Chữ Hán phải là đúng 1 ký tự Kanji duy nhất, ví dụ: 道.";

    private ValidationMessages() {
        // Ngăn khởi tạo lớp hằng số
    }
}

