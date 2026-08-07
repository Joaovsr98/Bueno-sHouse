package com.restaurante.sistema.common.exception;

/**
 * Excecao base para violacoes de regra de negocio (ex.: transicao de status invalida,
 * tentativa de fechar comanda com saldo devedor, etc.).
 * Mapeada para HTTP 422 pelo GlobalExceptionHandler.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
