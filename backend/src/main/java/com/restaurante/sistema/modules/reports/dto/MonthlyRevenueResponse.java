package com.restaurante.sistema.modules.reports.dto;

import java.math.BigDecimal;

/** Faturamento de um mes (month no formato "2026-05") e quantos pedidos o compuseram. */
public record MonthlyRevenueResponse(String month, BigDecimal revenue, long orders) {}
