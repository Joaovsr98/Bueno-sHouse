package com.restaurante.sistema.modules.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OpenCashRegisterRequest(
        @NotNull Long unitId,
        @NotNull @DecimalMin(value = "0.0", message = "O saldo inicial nao pode ser negativo")
        BigDecimal openingBalance
) {}
