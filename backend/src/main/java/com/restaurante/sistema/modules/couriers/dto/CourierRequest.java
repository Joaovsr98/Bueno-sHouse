package com.restaurante.sistema.modules.couriers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CourierRequest(
        @NotNull Long unitId,
        @NotNull(message = "O entregador precisa ter um usuario cadastrado (userId)")
        Long userId,
        @NotBlank String fullName,
        @NotBlank String phone,
        @NotBlank String document,
        String vehicleType,
        String plate
) {}
