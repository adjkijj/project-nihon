package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra âm đọc Onyomi:
 * Chỉ gồm Katakana, Hiragana, ー, "、", khoảng trắng, "-". Giá trị trống hợp lệ.
 */
@Documented
@Constraint(validatedBy = OnyomiReadingValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnyomiReading {

    String message() default ValidationMessages.ONYOMI_READING;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

