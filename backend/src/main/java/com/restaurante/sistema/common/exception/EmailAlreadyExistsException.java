package com.restaurante.sistema.common.exception;

/**
 * Lancada no cadastro publico (RN10) quando ja existe um usuario com o e-mail informado.
 * Mapeada para HTTP 409 pelo GlobalExceptionHandler.
 */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
