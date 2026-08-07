package com.restaurante.sistema.modules.cashregister.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashRegisterResponse(
        Long id,
        Long unitId,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal expectedBalance,
        BigDecimal difference,
        Instant openedAt,
        Instant closedAt,
        boolean open
) {}
