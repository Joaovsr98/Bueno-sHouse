package com.restaurante.sistema.modules.reports.dto;

import java.math.BigDecimal;

/**
 * RF-034: indicadores do dia (fuso de Sao Paulo). Faturamento e ticket medio
 * contam os pedidos finalizados hoje; "orders" conta todos os pedidos abertos
 * hoje que nao foram cancelados, e "inProgress" os que ainda estao andando.
 */
public record TodaySummaryResponse(
        String date,
        BigDecimal revenue,
        long orders,
        long completedOrders,
        long inProgress,
        BigDecimal averageTicket
) {}
