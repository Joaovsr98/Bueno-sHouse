package com.restaurante.sistema.modules.reports.service;

import com.restaurante.sistema.modules.inventory.dto.InventoryItemResponse;
import com.restaurante.sistema.modules.inventory.service.InventoryService;
import com.restaurante.sistema.modules.reports.dto.AverageTicketResponse;
import com.restaurante.sistema.modules.reports.dto.DailyRevenueResponse;
import com.restaurante.sistema.modules.reports.dto.MonthlyRevenueResponse;
import com.restaurante.sistema.modules.reports.dto.TopProductResponse;
import com.restaurante.sistema.modules.reports.dto.TodaySummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Etapa 12 (Fase 2) - relatorios administrativos e financeiros.
 *
 * As consultas replicam, em JPQL, exatamente o que foi desenhado na secao 10
 * da Etapa 3 (modelagem MySQL). Usa EntityManager diretamente em vez de um
 * repositorio Spring Data tradicional porque estas consultas agregam dados
 * de varias entidades sem pertencer a nenhuma delas especificamente.
 */
@Service
public class ReportService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Fuso do restaurante: "hoje", os dias e os meses dos relatorios sao os de Sao Paulo, nao os de UTC. */
    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

    private final InventoryService inventoryService;

    public ReportService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Vendas finalizadas a partir de um instante: [completedAt, total], agrupadas depois no fuso de SP. */
    @SuppressWarnings("unchecked")
    private List<Object[]> completedSince(Long unitId, java.time.Instant since) {
        return entityManager.createQuery(
                "SELECT o.completedAt, o.total FROM Order o " +
                "WHERE o.status = 'FINALIZADO' AND o.unitId = :unitId AND o.completedAt >= :since")
                .setParameter("unitId", unitId)
                .setParameter("since", since)
                .getResultList();
    }

    /** RF-036: vendas por dia dos ultimos N dias (dia de Sao Paulo), em ordem cronologica. */
    @Transactional(readOnly = true)
    public List<DailyRevenueResponse> dailyRevenue(Long unitId, int days) {
        java.time.Instant since = java.time.LocalDate.now(BUSINESS_ZONE)
                .minusDays(Math.max(1, days) - 1L).atStartOfDay(BUSINESS_ZONE).toInstant();
        return RevenueBuckets.byDay(completedSince(unitId, since), BUSINESS_ZONE).entrySet().stream()
                .map(e -> new DailyRevenueResponse(e.getKey().toString(), e.getValue().total()))
                .toList();
    }

    /** RF-034: faturamento, pedidos e ticket medio do dia. */
    @Transactional(readOnly = true)
    public TodaySummaryResponse today(Long unitId) {
        java.time.LocalDate today = java.time.LocalDate.now(BUSINESS_ZONE);
        java.time.Instant start = today.atStartOfDay(BUSINESS_ZONE).toInstant();
        java.time.Instant end = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

        Object[] done = (Object[]) entityManager.createQuery(
                "SELECT COALESCE(SUM(o.total), 0), COUNT(o) FROM Order o " +
                "WHERE o.status = 'FINALIZADO' AND o.unitId = :unitId AND o.completedAt >= :start AND o.completedAt < :end")
                .setParameter("unitId", unitId).setParameter("start", start).setParameter("end", end)
                .getSingleResult();
        Object[] opened = (Object[]) entityManager.createQuery(
                "SELECT COUNT(o), COALESCE(SUM(CASE WHEN o.status NOT IN ('FINALIZADO', 'ENTREGUE') THEN 1 ELSE 0 END), 0) " +
                "FROM Order o WHERE o.status <> 'CANCELADO' AND o.unitId = :unitId AND o.createdAt >= :start AND o.createdAt < :end")
                .setParameter("unitId", unitId).setParameter("start", start).setParameter("end", end)
                .getSingleResult();

        BigDecimal revenue = (BigDecimal) done[0];
        long completed = ((Number) done[1]).longValue();
        BigDecimal ticket = completed == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(completed), 2, java.math.RoundingMode.HALF_UP);
        return new TodaySummaryResponse(today.toString(), revenue, ((Number) opened[0]).longValue(), completed,
                ((Number) opened[1]).longValue(), ticket);
    }

    /** RF-037: vendas por mes (ultimos N meses), em ordem cronologica. */
    @Transactional(readOnly = true)
    public List<MonthlyRevenueResponse> monthlyRevenue(Long unitId, int months) {
        java.time.Instant since = java.time.LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1)
                .minusMonths(Math.max(1, months) - 1L)
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        return RevenueBuckets.byMonth(completedSince(unitId, since), BUSINESS_ZONE).entrySet().stream()
                .map(e -> new MonthlyRevenueResponse(e.getKey().toString(), e.getValue().total(), e.getValue().orders()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AverageTicketResponse averageTicket(Long unitId) {
        Query query = entityManager.createQuery(
                "SELECT COALESCE(AVG(o.total), 0), COUNT(o) FROM Order o " +
                "WHERE o.status = 'FINALIZADO' AND o.unitId = :unitId");
        query.setParameter("unitId", unitId);

        Object[] row = (Object[]) query.getSingleResult();
        BigDecimal avg = row[0] instanceof BigDecimal bd ? bd : BigDecimal.valueOf((Double) row[0]);
        long count = (Long) row[1];
        return new AverageTicketResponse(avg, count);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TopProductResponse> topProducts(Long unitId, int limit) {
        Query query = entityManager.createQuery(
                "SELECT oi.productNameSnapshot, SUM(oi.quantity) " +
                "FROM OrderItem oi JOIN oi.order o " +
                "WHERE o.status = 'FINALIZADO' AND o.unitId = :unitId " +
                "GROUP BY oi.productNameSnapshot ORDER BY SUM(oi.quantity) DESC");
        query.setParameter("unitId", unitId);
        query.setMaxResults(limit);

        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(r -> new TopProductResponse((String) r[0], (Long) r[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> lowStock(Long unitId) {
        return inventoryService.listBelowMinimum(unitId);
    }

    /** RF-034: food cost medio dos produtos com ficha tecnica cadastrada. */
    @Transactional(readOnly = true)
    public BigDecimal averageFoodCost(Long unitId) {
        return inventoryService.averageFoodCostPercent(unitId);
    }
}
