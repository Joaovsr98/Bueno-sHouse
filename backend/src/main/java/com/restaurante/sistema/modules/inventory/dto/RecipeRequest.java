package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record RecipeRequest(
        @NotNull Long productId,

        /** RF-011: rendimento da receita (quantas porcoes a ficha tecnica produz). */
        @NotNull @Positive BigDecimal yieldQuantity,

        @NotEmpty(message = "A ficha tecnica precisa ter pelo menos um ingrediente")
        @Valid
        List<RecipeItemRequest> items
) {}
