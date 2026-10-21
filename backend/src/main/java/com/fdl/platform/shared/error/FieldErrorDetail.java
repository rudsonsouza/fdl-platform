package com.fdl.platform.shared.error;

/** Um item de {@code errors[]} na resposta de erro de validação. */
public record FieldErrorDetail(String field, String message) {
}
