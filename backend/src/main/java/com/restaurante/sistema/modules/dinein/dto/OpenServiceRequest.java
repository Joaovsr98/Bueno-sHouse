package com.restaurante.sistema.modules.dinein.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OpenServiceRequest(
        @NotNull Long tableId,
        @Positive Integer partySize,
        String notes
) {}
