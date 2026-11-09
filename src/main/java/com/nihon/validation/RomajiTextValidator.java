package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link RomajiText}.
 * Cho phép null hoặc chuỗi trống (""). Nếu có dữ liệu, chỉ chấp nhận chữ Latin a-z A-Z,
 * nguyên âm có dấu trường âm (ā ī ū ē ō Ā Ī Ū Ē Ō), khoảng trắng và các dấu "'", "-", ",", ".", "/", "(", ")".
 */
public class RomajiTextValidator implements ConstraintValidator<RomajiText, String> {

    private static final String MACRON_VOWELS = "āīūēōĀĪŪĒŌ";

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
            if (!isAllowedRomajiChar(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean isAllowedRomajiChar(int cp) {
        if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
            return true;
        }
        if (MACRON_VOWELS.indexOf(cp) >= 0) {
            return true;
        }
        return cp == ' '
                || cp == '\''
                || cp == '-'
                || cp == ','
                || cp == '.'
                || cp == '/'
                || cp == '('
                || cp == ')';
    }
}

