package com.restaurante.sistema.modules.reports.dto;

import java.math.BigDecimal;

public record AverageTicketResponse(BigDecimal averageTicket, long orderCount) {}
