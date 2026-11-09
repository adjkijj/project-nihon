package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link SingleKanji}.
 * Không chấp nhận giá trị trống hoặc null. Chuỗi sau chuẩn hóa phải có đúng 1 code point
 * (kiểm tra bằng codePointCount, hỗ trợ cả Kanji nằm ngoài BMP) và thuộc Character.UnicodeScript.HAN.
 */
public class SingleKanjiValidator implements ConstraintValidator<SingleKanji, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (TextNormalizer.containsControlChars(value)) {
            return false;
        }
        String normalized = TextNormalizer.normalize(value);
        if (normalized == null) {
            return false;
        }
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            return false;
        }
        int cp = normalized.codePointAt(0);
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }
}

