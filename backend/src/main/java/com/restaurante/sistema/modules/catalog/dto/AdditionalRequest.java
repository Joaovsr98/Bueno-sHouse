package com.restaurante.sistema.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdditionalRequest(
        @NotBlank String name,
        @NotNull BigDecimal price
) {}
