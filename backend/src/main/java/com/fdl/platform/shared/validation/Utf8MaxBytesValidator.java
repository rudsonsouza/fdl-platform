package com.fdl.platform.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class Utf8MaxBytesValidator implements ConstraintValidator<Utf8MaxBytes, CharSequence> {

    private int max;

    @Override
    public void initialize(Utf8MaxBytes annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
