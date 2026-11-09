package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra cách đọc Kana (dùng cho trường reading và kunyomi):
 * Chỉ gồm Hiragana, Katakana, ー, ・, "、", khoảng trắng, dấu "/" và "-".
 * Giá trị null hoặc trống ("") là hợp lệ.
 */
@Documented
@Constraint(validatedBy = KanaReadingValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KanaReading {

    String message() default ValidationMessages.KANA_READING;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

