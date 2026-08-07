package com.restaurante.sistema.modules.ordering.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderItemRequest(
        @NotNull(message = "O produto e obrigatorio") Long productId,
        Long variationId,
        @Positive(message = "A quantidade deve ser maior que zero") int quantity,
        String notes,
        List<Long> additionalIds
) {}
