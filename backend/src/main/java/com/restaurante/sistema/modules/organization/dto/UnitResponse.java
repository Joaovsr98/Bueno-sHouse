package com.restaurante.sistema.modules.organization.dto;

public record UnitResponse(
        Long id,
        Long restaurantId,
        String restaurantName,
        String name,
        String address,
        String phone,
        String timezone,
        boolean active
) {}
