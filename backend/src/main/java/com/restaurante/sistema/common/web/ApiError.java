package com.restaurante.sistema.common.web;

import java.time.Instant;
import java.util.List;

/**
 * Formato padronizado de erro retornado por toda a API.
 * Todo endpoint que falhar retorna este formato, nunca a stack trace crua.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError ofValidation(int status, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, "Erro de validacao",
                "Um ou mais campos estao invalidos", path, fieldErrors);
    }
}
