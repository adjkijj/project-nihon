package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra phiên âm Romaji:
 * Chỉ gồm chữ Latin a-z A-Z, nguyên âm có dấu trường âm (ā ī ū ē ō Ā Ī Ū Ē Ō),
 * khoảng trắng, dấu "'", "-", ",", ".", "/", "(", ")". Giá trị trống hợp lệ.
 */
@Documented
@Constraint(validatedBy = RomajiTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RomajiText {

    String message() default ValidationMessages.ROMAJI_TEXT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

