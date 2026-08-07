package com.restaurante.sistema.modules.couriers.dto;

import jakarta.validation.constraints.NotBlank;

public record CourierStatusRequest(
        @NotBlank String status
) {}
