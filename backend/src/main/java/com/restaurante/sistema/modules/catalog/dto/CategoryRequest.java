package com.restaurante.sistema.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryRequest(
        @NotNull(message = "A unidade e obrigatoria")
        Long unitId,

        @NotBlank(message = "O nome da categoria e obrigatorio")
        String name,

        Integer displayOrder
) {}
