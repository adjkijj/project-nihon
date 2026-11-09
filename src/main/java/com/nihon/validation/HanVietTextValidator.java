package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link HanVietText}.
 * Cho phép null hoặc chuỗi trống (""). Nếu có dữ liệu, chỉ chấp nhận chữ cái in hoa tiếng Việt,
 * khoảng trắng và dấu phẩy (",").
 */
public class HanVietTextValidator implements ConstraintValidator<HanVietText, String> {

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

        boolean hasUppercaseLetter = false;
        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            if (isUppercaseVietnameseOrLatin(cp)) {
                hasUppercaseLetter = true;
            } else if (cp != ' ' && cp != ',') {
                return false;
            }
            i += Character.charCount(cp);
        }
        return hasUppercaseLetter;
    }

    private static boolean isUppercaseVietnameseOrLatin(int cp) {
        return (cp >= 'A' && cp <= 'Z') || VietnameseTextValidator.VIETNAMESE_UPPER.indexOf(cp) >= 0;
    }
}

