package com.restaurante.sistema.common.exception;

import com.restaurante.sistema.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Tratamento global de excecoes.
 *
 * Nenhum endpoint da aplicacao deve deixar uma excecao crua (stack trace) chegar
 * ao cliente. Toda resposta de erro segue o formato ApiError.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Nao encontrado", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthFailed(AuthenticationFailedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "Falha de autenticacao", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Regra de negocio violada", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({ConcurrencyConflictException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflito de concorrencia",
                        "Este registro foi alterado por outra operacao. Recarregue e tente novamente.",
                        req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.ofValidation(400, req.getRequestURI(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        // Erro nao mapeado explicitamente: nunca expor a mensagem interna/stack trace.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Erro interno",
                        "Ocorreu um erro inesperado. Tente novamente mais tarde.",
                        req.getRequestURI()));
    }
}
