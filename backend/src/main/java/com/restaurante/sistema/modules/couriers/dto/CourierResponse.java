package com.restaurante.sistema.modules.couriers.dto;

public record CourierResponse(
        Long id,
        Long unitId,
        String fullName,
        String phone,
        String vehicleType,
        String plate,
        String status
) {}
