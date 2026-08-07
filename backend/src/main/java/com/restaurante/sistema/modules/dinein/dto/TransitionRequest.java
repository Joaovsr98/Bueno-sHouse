package com.restaurante.sistema.modules.dinein.dto;

import jakarta.validation.constraints.NotBlank;

public record TransitionRequest(
        @NotBlank String status
) {}
