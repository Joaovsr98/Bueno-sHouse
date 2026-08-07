package com.restaurante.sistema.modules.ordering.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderTransitionRequest(
        @NotBlank String status,
        String reason
) {}
