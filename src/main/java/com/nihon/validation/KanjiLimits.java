package com.nihon.validation;

/**
 * Hằng số dùng chung quy định độ dài tối đa của các trường dữ liệu cho Kanji, Từ vựng và Chủ đề.
 * Được tham chiếu đồng bộ tại Entity (@Column), các bộ phân tích cú pháp (KanjiSheetParser,
 * WordSheetParser, VocabularyFileParser), ImportService và các Form DTO.
 */
public final class KanjiLimits {

    /** Độ dài tối đa tên chủ đề (topic.name). */
    public static final int MAX_TOPIC_LENGTH = 255;

    /** Độ dài tối đa cột ký tự Kanji trong database (kanji_entry.kanji_character). */
    public static final int MAX_KANJI_CHAR_LENGTH = 8;

    /** Độ dài tối đa âm Hán Việt của Kanji (kanji_entry.han_viet). */
    public static final int MAX_HAN_VIET_LENGTH = 255;

    /** Độ dài tối đa ý nghĩa tiếng Việt của Kanji (kanji_entry.meaning_vi). */
    public static final int MAX_KANJI_MEANING_LENGTH = 255;

    /** Độ dài tối đa âm On của Kanji (kanji_entry.onyomi). */
    public static final int MAX_ONYOMI_LENGTH = 255;

    /** Độ dài tối đa âm Kun của Kanji (kanji_entry.kunyomi). */
    public static final int MAX_KUNYOMI_LENGTH = 255;

    /** Độ dài tối đa dùng chung cho âm On và âm Kun của Kanji. */
    public static final int MAX_ON_KUN_LENGTH = 255;

    /** Độ dài tối đa từ tiếng Nhật (vocabulary.japanese_text). */
    public static final int MAX_JAPANESE_LENGTH = 255;

    /** Độ dài tối đa cách đọc Hiragana/Katakana của từ vựng (vocabulary.reading). */
    public static final int MAX_READING_LENGTH = 255;

    /** Độ dài tối đa phiên âm Romaji (vocabulary.romaji). */
    public static final int MAX_ROMAJI_LENGTH = 255;

    /** Độ dài tối đa nghĩa tiếng Việt của từ vựng / từ cần nhớ (vocabulary.meaning_vi). */
    public static final int MAX_VOCAB_MEANING_LENGTH = 500;

    private KanjiLimits() {
        // Lớp tiện ích chứa hằng số, không cho phép khởi tạo
    }
}

