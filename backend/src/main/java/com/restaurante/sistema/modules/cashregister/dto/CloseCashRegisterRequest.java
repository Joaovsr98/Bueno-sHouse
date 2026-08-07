package com.restaurante.sistema.modules.cashregister.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashRegisterRequest(
        @NotNull BigDecimal closingBalance
) {}
