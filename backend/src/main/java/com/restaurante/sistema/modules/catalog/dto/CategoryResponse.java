package com.restaurante.sistema.modules.catalog.dto;

public record CategoryResponse(
        Long id,
        Long unitId,
        String name,
        int displayOrder,
        boolean active
) {}
