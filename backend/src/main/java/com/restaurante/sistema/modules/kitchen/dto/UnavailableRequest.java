package com.restaurante.sistema.modules.kitchen.dto;

import jakarta.validation.constraints.NotBlank;

public record UnavailableRequest(
        @NotBlank(message = "O motivo e obrigatorio") String reason
) {}
