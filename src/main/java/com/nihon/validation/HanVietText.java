package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra âm Hán Việt:
 * Chỉ gồm chữ in hoa tiếng Việt, khoảng trắng và dấu phẩy. Giá trị trống hợp lệ.
 */
@Documented
@Constraint(validatedBy = HanVietTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HanVietText {

    String message() default ValidationMessages.HAN_VIET_TEXT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

