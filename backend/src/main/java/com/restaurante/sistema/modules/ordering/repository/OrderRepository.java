package com.restaurante.sistema.modules.ordering.repository;

import com.restaurante.sistema.modules.ordering.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUnitIdAndStatusIn(Long unitId, List<String> statuses);

    org.springframework.data.domain.Page<Order> findByUnitIdAndStatusIn(Long unitId, List<String> statuses,
            org.springframework.data.domain.Pageable pageable);

    /** Painel de pedidos (RF-019): filtros opcionais de canal e periodo (null = sem filtro). */
    @Query("SELECT o FROM Order o WHERE o.unitId = :unitId AND o.status IN :statuses"
            + " AND (:channel IS NULL OR o.channel = :channel)"
            + " AND (:fromInstant IS NULL OR o.createdAt >= :fromInstant)"
            + " AND (:toInstant IS NULL OR o.createdAt < :toInstant)")
    org.springframework.data.domain.Page<Order> search(
            @Param("unitId") Long unitId,
            @Param("statuses") List<String> statuses,
            @Param("channel") String channel,
            @Param("fromInstant") java.time.Instant fromInstant,
            @Param("toInstant") java.time.Instant toInstant,
            org.springframework.data.domain.Pageable pageable);

    List<Order> findByCommandId(Long commandId);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("SELECT COALESCE(MAX(o.orderNumber), 0) FROM Order o WHERE o.unitId = :unitId")
    Long findMaxOrderNumberForUnit(@Param("unitId") Long unitId);
}
