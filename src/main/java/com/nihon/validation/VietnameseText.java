package com.nihon.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ràng buộc kiểm tra văn bản tiếng Việt:
 * Cho phép chữ Latin kèm dấu tiếng Việt, số, khoảng trắng và dấu câu thông thường.
 * Không cho phép ký tự điều khiển, không cho phép "<" và ">". Giá trị trống hợp lệ.
 */
@Documented
@Constraint(validatedBy = VietnameseTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface VietnameseText {

    String message() default ValidationMessages.VIETNAMESE_TEXT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

