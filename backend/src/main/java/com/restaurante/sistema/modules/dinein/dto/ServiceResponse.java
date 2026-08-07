package com.restaurante.sistema.modules.dinein.dto;

import java.time.Instant;

public record ServiceResponse(
        Long id,
        Long tableId,
        String tableNumber,
        int partySize,
        String status,
        String notes,
        Instant openedAt,
        Instant closedAt
) {}
