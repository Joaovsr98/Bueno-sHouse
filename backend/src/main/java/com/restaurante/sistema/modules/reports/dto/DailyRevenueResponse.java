package com.restaurante.sistema.modules.reports.dto;

import java.math.BigDecimal;

public record DailyRevenueResponse(String day, BigDecimal revenue) {}
