package com.restaurante.sistema.modules.dinein.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
        @NotBlank(message = "A justificativa e obrigatoria para cancelamento")
        String reason
) {}
