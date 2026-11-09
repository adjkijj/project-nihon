package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra ký tự Kanji đơn lẻ:
 * Đúng 1 code point thuộc UnicodeScript.HAN (tính bằng codePointCount, không dùng length()). Không trống.
 */
@Documented
@Constraint(validatedBy = SingleKanjiValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SingleKanji {

    String message() default ValidationMessages.SINGLE_KANJI;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

