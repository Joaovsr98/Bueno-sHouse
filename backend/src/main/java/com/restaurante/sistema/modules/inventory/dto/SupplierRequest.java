package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupplierRequest(
        @NotNull Long unitId,
        @NotBlank String name,
        String phone,

        /** RN07: validado como CNPJ (com ou sem mascara). */
        @NotBlank String document
) {}
