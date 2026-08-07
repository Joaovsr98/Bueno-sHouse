package com.restaurante.sistema.modules.delivery.dto;

import java.math.BigDecimal;

public record DeliveryZoneResponse(
        Long id,
        Long unitId,
        String zoneName,
        String neighborhood,
        BigDecimal fee,
        BigDecimal minimumOrderValue,
        Integer estimatedMinutes,
        boolean active
) {}
