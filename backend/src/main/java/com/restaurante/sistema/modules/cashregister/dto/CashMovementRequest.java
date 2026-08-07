package com.restaurante.sistema.modules.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashMovementRequest(
        @NotBlank String type, // ENTRADA, SAIDA, SANGRIA, SUPRIMENTO
        @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal amount,
        String reason
) {}
