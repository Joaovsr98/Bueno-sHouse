package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecipeRequest(
        @NotNull Long productId,
        @NotEmpty(message = "A ficha tecnica precisa ter pelo menos um ingrediente")
        @Valid
        List<RecipeItemRequest> items
) {}
