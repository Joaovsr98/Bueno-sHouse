package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PurchaseRequest(
        @NotNull Long unitId,
        @NotNull Long supplierId,
        @NotEmpty(message = "O pedido de compra precisa ter pelo menos um item")
        @Valid
        List<PurchaseItemRequest> items
) {}
