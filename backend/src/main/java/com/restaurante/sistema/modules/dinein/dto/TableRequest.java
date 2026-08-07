package com.restaurante.sistema.modules.dinein.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TableRequest(
        @NotNull Long unitId,
        @NotBlank String number,
        @Positive Integer capacity
) {}
