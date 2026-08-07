package com.restaurante.sistema.modules.dinein.dto;

import java.time.Instant;

public record CommandResponse(
        Long id,
        Long serviceId,
        String status,
        Instant openedAt,
        Instant closedAt
) {}
