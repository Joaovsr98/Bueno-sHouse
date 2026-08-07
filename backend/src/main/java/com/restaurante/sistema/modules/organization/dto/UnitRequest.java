package com.restaurante.sistema.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UnitRequest(
        @NotNull(message = "O restaurante e obrigatorio")
        Long restaurantId,

        @NotBlank(message = "O nome da unidade e obrigatorio")
        String name,

        String address,
        String phone,
        String timezone
) {}
