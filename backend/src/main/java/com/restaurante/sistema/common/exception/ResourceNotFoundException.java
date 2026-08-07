package com.restaurante.sistema.common.exception;

/**
 * Lancada quando uma entidade referenciada por ID nao existe.
 * Mapeada para HTTP 404 pelo GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String entity, Object id) {
        super(entity + " nao encontrado(a) com id: " + id);
    }
}
