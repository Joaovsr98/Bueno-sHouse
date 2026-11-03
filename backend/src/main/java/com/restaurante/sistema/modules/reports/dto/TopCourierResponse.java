package com.restaurante.sistema.modules.reports.dto;

import java.math.BigDecimal;

/** Motoboy no ranking: entregas concluidas, taxas somadas e tempo medio da saida ate a entrega. */
public record TopCourierResponse(String courierName, long deliveries, BigDecimal fees, Integer averageMinutes) {}
