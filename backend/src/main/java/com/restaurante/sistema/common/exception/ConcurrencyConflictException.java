package com.restaurante.sistema.common.exception;

/**
 * Lancada quando uma operacao otimista (baseada em @Version) falha porque outro
 * usuario/processo alterou o registro primeiro (ex.: dois entregadores aceitando
 * a mesma entrega, dois caixas fechando a mesma comanda).
 * Mapeada para HTTP 409 pelo GlobalExceptionHandler.
 */
public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
