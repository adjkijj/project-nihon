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

    private boolean allowNewlines = false;
    private boolean allowJapanese = false;

    @Override
    public void initialize(VietnameseText constraintAnnotation) {
        if (constraintAnnotation != null) {
            this.allowNewlines = constraintAnnotation.allowNewlines();
            this.allowJapanese = constraintAnnotation.allowJapanese();
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (TextNormalizer.containsControlChars(value, allowNewlines)) {
            return false;
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            return false;
        }

        if (!allowNewlines) {
            String normalized = TextNormalizer.normalize(value);
            if (normalized == null) {
                return false;
            }
            for (int i = 0; i < normalized.length(); ) {
                int cp = normalized.codePointAt(i);
                if (!isAllowedChar(cp)) {
                    return false;
                }
                i += Character.charCount(cp);
            }
            return true;
        } else {
            // Trường hợp cho phép xuống dòng
            if (value.trim().isEmpty()) {
                return false;
            }
            String nfc = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC);
            boolean hasNonWhitespace = false;
            for (int i = 0; i < nfc.length(); ) {
                int cp = nfc.codePointAt(i);
                if (cp == '\n' || cp == '\r') {
                    i += Character.charCount(cp);
                    continue;
                }
                if (!isAllowedChar(cp)) {
                    return false;
                }
                if (!Character.isWhitespace(cp)) {
                    hasNonWhitespace = true;
                }
                i += Character.charCount(cp);
            }
            return hasNonWhitespace;
        }
    }

    private boolean isAllowedChar(int cp) {
        if (isAllowedVietnameseChar(cp)) {
            return true;
        }
        if (allowJapanese) {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN) {
                return true;
            }
            if (cp == '〜' || cp == '～' || cp == '・' || cp == '･' || cp == 'ー'
                    || cp == '「' || cp == '」' || cp == '『' || cp == '』'
                    || cp == '【' || cp == '】' || cp == '、' || cp == '。'
                    || cp == '（' || cp == '）' || cp == '？' || cp == '！'
                    || cp == '\u3000') {
                return true;
            }
        }
        return false;
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

