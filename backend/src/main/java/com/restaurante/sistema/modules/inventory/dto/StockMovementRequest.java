package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** type: ENTRADA_COMPRA, AJUSTE_PERDA - movimentos manuais. A baixa por producao (SAIDA_PRODUCAO) e sempre automatica. */
public record StockMovementRequest(
        @NotBlank String type,
        @NotNull @Positive BigDecimal quantity,
        String reason
) {}
