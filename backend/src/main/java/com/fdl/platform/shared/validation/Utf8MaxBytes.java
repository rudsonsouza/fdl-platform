package com.fdl.platform.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limita o tamanho em bytes UTF-8 (o BCrypt considera só os 72 primeiros bytes da senha). */
@Documented
@Constraint(validatedBy = Utf8MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8MaxBytes {

    int value();

    String message() default "deve ter no máximo {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
