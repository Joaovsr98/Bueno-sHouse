package com.restaurante.sistema.modules.reports.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Agrupa vendas por dia ou mes no fuso do restaurante. O banco guarda os horarios em
 * UTC: agrupar por DATE() no SQL jogava um pedido das 22h de segunda (01h UTC de terca)
 * na terca, enquanto o card "Hoje" ja usava o dia de Sao Paulo.
 */
final class RevenueBuckets {

    /** Total e quantidade de pedidos de um periodo. */
    record Bucket(BigDecimal total, long orders) {
        Bucket plus(BigDecimal value) {
            return new Bucket(total.add(value), orders + 1);
        }
    }

    private RevenueBuckets() {}

    /** rows: [Instant completedAt, BigDecimal total]. Ordem cronologica. */
    static Map<LocalDate, Bucket> byDay(List<Object[]> rows, ZoneId zone) {
        Map<LocalDate, Bucket> out = new TreeMap<>();
        for (Object[] r : rows) {
            LocalDate day = ((Instant) r[0]).atZone(zone).toLocalDate();
            out.merge(day, new Bucket(BigDecimal.ZERO, 0).plus((BigDecimal) r[1]),
                    (a, b) -> new Bucket(a.total().add(b.total()), a.orders() + b.orders()));
        }
        return out;
    }

    static Map<YearMonth, Bucket> byMonth(List<Object[]> rows, ZoneId zone) {
        Map<YearMonth, Bucket> out = new TreeMap<>();
        byDay(rows, zone).forEach((day, b) -> out.merge(YearMonth.from(day), b,
                (x, y) -> new Bucket(x.total().add(y.total()), x.orders() + y.orders())));
        return out;
    }
}
