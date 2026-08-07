package com.restaurante.sistema.modules.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryFailureRequest(
        @NotBlank String reason,
        String nextAction
) {}
