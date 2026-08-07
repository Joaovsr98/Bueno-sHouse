package com.restaurante.sistema.modules.delivery.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DeliveryZoneRequest(
        @NotNull Long unitId,
        @NotBlank String zoneName,
        @NotBlank String neighborhood,
        @NotNull @DecimalMin(value = "0.0", message = "A taxa nao pode ser negativa") BigDecimal fee,
        BigDecimal minimumOrderValue,
        Integer estimatedMinutes
) {}
