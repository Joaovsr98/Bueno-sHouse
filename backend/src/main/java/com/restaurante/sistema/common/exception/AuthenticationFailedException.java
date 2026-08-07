package com.restaurante.sistema.common.exception;

/**
 * Lancada em falha de login (credenciais invalidas ou conta bloqueada).
 * Mapeada para HTTP 401 pelo GlobalExceptionHandler. A mensagem nunca revela
 * se o e-mail existe ou nao (evita enumeracao de usuarios).
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
