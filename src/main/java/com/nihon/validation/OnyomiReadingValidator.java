package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link OnyomiReading}.
 * Cho phép null hoặc chuỗi trống (""). Nếu có dữ liệu, chỉ chấp nhận Katakana, Hiragana,
 * dấu ー, "、", khoảng trắng và "-".
 */
public class OnyomiReadingValidator implements ConstraintValidator<OnyomiReading, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (TextNormalizer.containsControlChars(value)) {
            return false;
        }
        String normalized = TextNormalizer.normalize(value);
        if (normalized == null) {
            return false;
        }

        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script != Character.UnicodeScript.KATAKANA
                    && script != Character.UnicodeScript.HIRAGANA
                    && !isAllowedOnyomiSymbol(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean isAllowedOnyomiSymbol(int cp) {
        return cp == 'ー'
                || cp == '、'
                || cp == ' ' || cp == '\u3000'
                || cp == '-' || cp == '－' || cp == '‐';
    }
}

