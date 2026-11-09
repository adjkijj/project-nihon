package com.nihon.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Bộ kiểm tra cho ràng buộc {@link NoControlChars}.
 * Từ chối mọi chuỗi chứa ký tự điều khiển (Character.isISOControl) và ký tự xuống dòng (trừ khi allowNewlines = true).
 */
public class NoControlCharsValidator implements ConstraintValidator<NoControlChars, String> {

    private boolean allowNewlines;

    @Override
    public void initialize(NoControlChars constraintAnnotation) {
        this.allowNewlines = constraintAnnotation.allowNewlines();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return !TextNormalizer.containsControlChars(value, allowNewlines);
    }
}

