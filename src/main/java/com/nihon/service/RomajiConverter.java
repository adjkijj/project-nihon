package com.nihon.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiện ích chuyển đổi Romaji kiểu Hepburn thông dụng sang chữ Hiragana và Katakana.
 * Dùng để chuẩn hóa từ khóa tìm kiếm âm On/Kun cho Kanji.
 */
public final class RomajiConverter {

    private static final Map<String, String> ROMAJI_TO_HIRAGANA;

    static {
        Map<String, String> map = new LinkedHashMap<>();

        // 3-4 ký tự (hợp âm và âm ghép đặc biệt)
        map.put("kya", "きゃ"); map.put("kyu", "きゅ"); map.put("kyo", "きょ"); map.put("kye", "きぇ");
        map.put("gya", "ぎゃ"); map.put("gyu", "ぎゅ"); map.put("gyo", "ぎょ"); map.put("gye", "ぎぇ");
        map.put("sha", "しゃ"); map.put("shu", "しゅ"); map.put("sho", "しょ"); map.put("she", "しぇ");
        map.put("sya", "しゃ"); map.put("syu", "しゅ"); map.put("syo", "しょ"); map.put("sye", "しぇ");
        map.put("cha", "ちゃ"); map.put("chu", "ちゅ"); map.put("cho", "ちょ"); map.put("che", "ちぇ");
        map.put("tya", "ちゃ"); map.put("tyu", "ちゅ"); map.put("tyo", "ちょ"); map.put("tye", "ちぇ");
        map.put("nya", "にゃ"); map.put("nyu", "にゅ"); map.put("nyo", "にょ"); map.put("nye", "にぇ");
        map.put("hya", "ひゃ"); map.put("hyu", "ひゅ"); map.put("hyo", "ひょ"); map.put("hye", "ひぇ");
        map.put("bya", "びゃ"); map.put("byu", "びゅ"); map.put("byo", "びょ"); map.put("bye", "びぇ");
        map.put("pya", "ぴゃ"); map.put("pyu", "ぴゅ"); map.put("pyo", "ぴょ"); map.put("pye", "ぴぇ");
        map.put("mya", "みゃ"); map.put("myu", "みゅ"); map.put("myo", "みょ"); map.put("mye", "みぇ");
        map.put("rya", "りゃ"); map.put("ryu", "りゅ"); map.put("ryo", "りょ"); map.put("rye", "りぇ");
        map.put("tsu", "つ");
        map.put("shi", "し"); map.put("chi", "ち");
        map.put("tchi", "っち"); map.put("cchi", "っち");

        // 2 ký tự (âm đơn và hàng đặc biệt)
        map.put("ka", "か"); map.put("ki", "き"); map.put("ku", "く"); map.put("ke", "け"); map.put("ko", "こ");
        map.put("ga", "が"); map.put("gi", "ぎ"); map.put("gu", "ぐ"); map.put("ge", "げ"); map.put("go", "ご");
        map.put("sa", "さ"); map.put("si", "し"); map.put("su", "す"); map.put("se", "せ"); map.put("so", "そ");
        map.put("za", "ざ"); map.put("zi", "じ"); map.put("zu", "ず"); map.put("ze", "ぜ"); map.put("zo", "ぞ");
        map.put("ji", "じ");
        map.put("ta", "た"); map.put("ti", "ち"); map.put("tu", "つ"); map.put("te", "て"); map.put("to", "と");
        map.put("da", "だ"); map.put("di", "ぢ"); map.put("du", "づ"); map.put("de", "で"); map.put("do", "ど");
        map.put("na", "な"); map.put("ni", "に"); map.put("nu", "ぬ"); map.put("ne", "ね"); map.put("no", "の");
        map.put("ha", "は"); map.put("hi", "ひ"); map.put("fu", "ふ"); map.put("hu", "ふ"); map.put("he", "へ"); map.put("ho", "ほ");
        map.put("ba", "ば"); map.put("bi", "び"); map.put("bu", "ぶ"); map.put("be", "べ"); map.put("bo", "ぼ");
        map.put("pa", "ぱ"); map.put("pi", "ぴ"); map.put("pu", "ぷ"); map.put("pe", "ぺ"); map.put("po", "ぽ");
        map.put("ma", "ま"); map.put("mi", "み"); map.put("mu", "む"); map.put("me", "め"); map.put("mo", "も");
        map.put("ya", "や"); map.put("yu", "ゆ"); map.put("yo", "よ");
        map.put("ra", "ら"); map.put("ri", "り"); map.put("ru", "る"); map.put("re", "れ"); map.put("ro", "ろ");
        map.put("wa", "わ"); map.put("wo", "を");
        map.put("ja", "じゃ"); map.put("ju", "じゅ"); map.put("jo", "じょ"); map.put("je", "じぇ");
        map.put("fa", "ふぁ"); map.put("fi", "ふぃ"); map.put("fe", "ふぇ"); map.put("fo", "ふぉ");
        map.put("n'", "ん");

        // 1 ký tự nguyên âm
        map.put("a", "あ"); map.put("i", "い"); map.put("u", "う"); map.put("e", "え"); map.put("o", "お");

        ROMAJI_TO_HIRAGANA = Collections.unmodifiableMap(map);
    }

    private RomajiConverter() {
        // Utility class
    }

    /**
     * Chuyển đổi chuỗi Romaji Hepburn sang Hiragana.
     * Hỗ trợ nguyên âm dài (ā, ī, ū, ē, ō), phụ âm kép (sokuon っ), âm ngắt n/nn/n'.
     * Ký tự không thuộc Romaji (chữ Hán, Kana có sẵn, dấu cách, số, ký tự đặc biệt) được giữ nguyên.
     *
     * @param input chuỗi đầu vào (có thể là romaji, kana hoặc hỗn hợp)
     * @return chuỗi đã chuyển sang Hiragana, hoặc chuỗi rỗng nếu input null/blank
     */
    public static String toHiragana(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Chuẩn hóa nguyên âm dài (Hepburn macrons & circumflex)
        String text = normalizeLongVowels(input.toLowerCase().strip());

        StringBuilder result = new StringBuilder();
        int len = text.length();
        int i = 0;

        while (i < len) {
            // Kiểm tra trường hợp 'n' đứng riêng
            char c = text.charAt(i);
            if (c == 'n') {
                if (i + 1 == len) {
                    result.append('ん');
                    i++;
                    continue;
                }
                char next = text.charAt(i + 1);
                if (next == '\'') {
                    result.append('ん');
                    i += 2;
                    continue;
                }
                // Nếu là 'nn'
                if (next == 'n') {
                    // Nếu sau 'nn' là nguyên âm hoặc y (ví dụ: konnichiwa -> n + ni), chỉ tiêu thụ 1 chữ n
                    if (i + 2 < len && (isVowel(text.charAt(i + 2)) || text.charAt(i + 2) == 'y')) {
                        result.append('ん');
                        i++;
                        continue;
                    } else {
                        result.append('ん');
                        i += 2;
                        continue;
                    }
                }
                // Nếu n đi trước phụ âm không phải y và không phải nguyên âm a, i, u, e, o
                if (isConsonantExceptY(next)) {
                    result.append('ん');
                    i++;
                    continue;
                }
            }

            // Kiểm tra khớp từ điển Romaji trước (ưu tiên từ dài 4, 3, 2, 1 ký tự)
            boolean matched = false;
            int maxMatchLen = Math.min(4, len - i);
            for (int subLen = maxMatchLen; subLen >= 1; subLen--) {
                String sub = text.substring(i, i + subLen);
                if (ROMAJI_TO_HIRAGANA.containsKey(sub)) {
                    result.append(ROMAJI_TO_HIRAGANA.get(sub));
                    i += subLen;
                    matched = true;
                    break;
                }
            }
            if (matched) {
                continue;
            }

            // Kiểm tra phụ âm kép (sokuon っ): ví dụ kk, tt, pp, ss, tch, cch
            if (i + 1 < len) {
                char next = text.charAt(i + 1);
                if (c == next && isConsonant(c) && c != 'n') {
                    result.append('っ');
                    i++;
                    continue;
                }
                if (c == 't' && next == 'c') {
                    result.append('っ');
                    i++;
                    continue;
                }
            }

            // Nếu không khớp từ điển Romaji, giữ nguyên ký tự hiện tại (kana, số, ký tự lạ...)
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Chuyển Hiragana sang Katakana tương ứng.
     */
    public static String toKatakana(String hiragana) {
        if (hiragana == null || hiragana.isBlank()) {
            return hiragana != null ? hiragana : "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hiragana.length(); i++) {
            char c = hiragana.charAt(i);
            if (c >= 0x3041 && c <= 0x3096) {
                sb.append((char) (c + 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Chuyển Katakana sang Hiragana tương ứng.
     */
    public static String katakanaToHiragana(String katakana) {
        if (katakana == null || katakana.isBlank()) {
            return katakana != null ? katakana : "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < katakana.length(); i++) {
            char c = katakana.charAt(i);
            if (c >= 0x30A1 && c <= 0x30F6) {
                sb.append((char) (c - 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalizeLongVowels(String s) {
        return s.replace("ā", "aa")
                .replace("ī", "ii")
                .replace("ū", "uu")
                .replace("ē", "ee")
                .replace("ō", "ou")
                .replace("â", "aa")
                .replace("î", "ii")
                .replace("û", "uu")
                .replace("ê", "ee")
                .replace("ô", "ou");
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private static boolean isConsonant(char c) {
        return c >= 'a' && c <= 'z' && !isVowel(c);
    }

    private static boolean isConsonantExceptY(char c) {
        return isConsonant(c) && c != 'y';
    }
}
