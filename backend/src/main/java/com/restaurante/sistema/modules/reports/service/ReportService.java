package com.restaurante.sistema.modules.reports.service;

import com.restaurante.sistema.modules.inventory.dto.InventoryItemResponse;
import com.restaurante.sistema.modules.inventory.service.InventoryService;
import com.restaurante.sistema.modules.reports.dto.AverageTicketResponse;
import com.restaurante.sistema.modules.reports.dto.DailyRevenueResponse;
import com.restaurante.sistema.modules.reports.dto.TopProductResponse;
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

    private final InventoryService inventoryService;

    public ReportService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<DailyRevenueResponse> dailyRevenue(Long unitId) {
        Query query = entityManager.createQuery(
                "SELECT FUNCTION('DATE', o.completedAt), SUM(o.total) " +
                "FROM Order o WHERE o.status = 'FINALIZADO' AND o.unitId = :unitId " +
                "GROUP BY FUNCTION('DATE', o.completedAt) ORDER BY FUNCTION('DATE', o.completedAt) DESC");
        query.setParameter("unitId", unitId);

        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(r -> new DailyRevenueResponse(String.valueOf(r[0]), (BigDecimal) r[1]))
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
}
