package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link JapaneseText}.
 * Yêu cầu có ít nhất một ký tự thuộc Hiragana, Katakana hoặc Han (Character.UnicodeScript).
 * Chỉ cho phép thêm chữ số, dấu ngoặc Nhật và ngoặc thường, dấu 〜, ー, ・, khoảng trắng, dấu "/" và "、".
 */
public class JapaneseTextValidator implements ConstraintValidator<JapaneseText, String> {

    private boolean optional;

    @Override
    public void initialize(JapaneseText constraintAnnotation) {
        this.optional = constraintAnnotation.optional();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        if (TextNormalizer.containsControlChars(value)) {
            return false;
        }
        String normalized = TextNormalizer.normalize(value);
        if (normalized == null) {
            return false;
        }

        boolean hasJapaneseScript = false;
        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN) {
                hasJapaneseScript = true;
            } else if (!isAllowedAuxiliaryChar(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return hasJapaneseScript;
    }

    private static boolean isAllowedAuxiliaryChar(int cp) {
        // Chữ số ASCII và chữ số full-width tiếng Nhật
        if ((cp >= '0' && cp <= '9') || (cp >= '０' && cp <= '９')) {
            return true;
        }
        // Dấu ngoặc thường và dấu ngoặc Nhật
        if (cp == '(' || cp == ')' || cp == '[' || cp == ']'
                || cp == '（' || cp == '）'
                || cp == '「' || cp == '」'
                || cp == '『' || cp == '』'
                || cp == '【' || cp == '】'
                || cp == '〔' || cp == '〕'
                || cp == '［' || cp == '］') {
            return true;
        }
        // Dấu 〜 (U+301C, U+FF5E, ~), ー (U+30FC), ・ (U+30FB, U+FF65), khoảng trắng, "/" và "、", "。", "?", "？", "!", "！", "_", "＿", "★", "☆", "*", ":", "："
        return cp == '〜' || cp == '～' || cp == '~'
                || cp == 'ー'
                || cp == '・' || cp == '･'
                || cp == ' ' || cp == '\u3000'
                || cp == '/' || cp == '／'
                || cp == '、' || cp == '。'
                || cp == '?' || cp == '？'
                || cp == '!' || cp == '！'
                || cp == '_' || cp == '＿'
                || cp == '★' || cp == '☆' || cp == '*'
                || cp == ':' || cp == '：';
    }
}

