package com.restaurante.sistema.modules.reports.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Vendas agrupadas no dia/mes de Sao Paulo, nao no de UTC. */
class RevenueBucketsTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void lateNightSaleBelongsToTheSaoPauloDay() {
        // 22h de 27/10 em Sao Paulo = 01h de 28/10 em UTC
        List<Object[]> rows = List.of(
                new Object[] {Instant.parse("2026-10-28T01:00:00Z"), new BigDecimal("40.00")},
                new Object[] {Instant.parse("2026-10-27T15:00:00Z"), new BigDecimal("10.00")});

        Map<LocalDate, RevenueBuckets.Bucket> days = RevenueBuckets.byDay(rows, SP);

        assertThat(days).containsOnlyKeys(LocalDate.of(2026, 10, 27));
        assertThat(days.get(LocalDate.of(2026, 10, 27)).total()).isEqualByComparingTo("50.00");
        assertThat(days.get(LocalDate.of(2026, 10, 27)).orders()).isEqualTo(2);
    }

    @Test
    void lastNightOfTheMonthStaysInThatMonth() {
        // 23h de 31/10 em SP = 02h de 01/11 em UTC: venda de outubro
        List<Object[]> rows = List.<Object[]>of(new Object[] {Instant.parse("2026-11-01T02:00:00Z"), new BigDecimal("30.00")});

        Map<YearMonth, RevenueBuckets.Bucket> months = RevenueBuckets.byMonth(rows, SP);

        assertThat(months).containsOnlyKeys(YearMonth.of(2026, 10));
    }
}
