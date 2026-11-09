package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra chuỗi không chứa ký tự điều khiển (ISO control) và không chứa dấu xuống dòng.
 */
@Documented
@Constraint(validatedBy = NoControlCharsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoControlChars {

    String message() default ValidationMessages.NO_CONTROL_CHARS;

    /**
     * Tùy chọn cho phép ký tự xuống dòng (\r, \n) trong những trường đặc biệt được phép. Mặc định là false.
     */
    boolean allowNewlines() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

