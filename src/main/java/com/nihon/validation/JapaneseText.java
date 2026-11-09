package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra văn bản tiếng Nhật (dùng cho trường japaneseText):
 * Phải có ít nhất một ký tự Hiragana, Katakana hoặc Hán (Character.UnicodeScript).
 * Cho phép số, dấu ngoặc Nhật và ngoặc thường, dấu 〜, ー, ・, khoảng trắng, dấu "/" và "、".
 */
@Documented
@Constraint(validatedBy = JapaneseTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JapaneseText {

    String message() default ValidationMessages.JAPANESE_TEXT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

