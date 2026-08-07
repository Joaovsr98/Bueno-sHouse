package com.restaurante.sistema.modules.dinein.dto;

public record TableResponse(
        Long id,
        Long unitId,
        String number,
        int capacity,
        String status
) {}
