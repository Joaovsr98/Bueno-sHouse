package com.restaurante.sistema.modules.payments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * cashRegisterId e obrigatorio apenas quando method = DINHEIRO (precisa
 * bater com o caixa aberto da unidade para entrar no saldo esperado do
 * fechamento). Para os demais metodos e opcional/ignorado nesta fase, ja
 * que nao ha integracao de gateway (decisao aprovada na Etapa 1).
 */
public record RegisterPaymentRequest(
        @NotNull Long orderId,
        @NotBlank String method,
        @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal amount,
        Long cashRegisterId
) {}
