package com.restaurante.sistema.modules.catalog.dto;

import java.util.List;

public record AdditionalGroupResponse(
        Long id,
        Long unitId,
        String name,
        int minQuantity,
        int maxQuantity,
        boolean required,
        List<AdditionalResponse> additionals
) {}
