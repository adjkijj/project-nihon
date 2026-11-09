package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link VietnameseText}.
 * Cho phép null hoặc chuỗi trống (""). Nếu có dữ liệu, chỉ chấp nhận chữ cái Latin và chữ cái có dấu tiếng Việt,
 * chữ số, khoảng trắng và dấu câu thông thường. Cấm ký tự điều khiển và cấm "<", ">".
 */
public class VietnameseTextValidator implements ConstraintValidator<VietnameseText, String> {

    static final String VIETNAMESE_LOWER =
            "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ";

    static final String VIETNAMESE_UPPER =
            "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬĐÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴ";

    private static final String COMMON_PUNCTUATION = ".,;:!?'\"()-–—/[]{}%&+_~“”‘’…";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (TextNormalizer.containsControlChars(value)) {
            return false;
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            return false;
        }
        String normalized = TextNormalizer.normalize(value);
        if (normalized == null) {
            return false;
        }

        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            if (!isAllowedVietnameseChar(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean isAllowedVietnameseChar(int cp) {
        if (cp == '<' || cp == '>') {
            return false;
        }
        if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
            return true;
        }
        if (VIETNAMESE_LOWER.indexOf(cp) >= 0 || VIETNAMESE_UPPER.indexOf(cp) >= 0) {
            return true;
        }
        if (cp >= '0' && cp <= '9') {
            return true;
        }
        if (cp == ' ') {
            return true;
        }
        return COMMON_PUNCTUATION.indexOf(cp) >= 0;
    }
}

