package com.restaurante.sistema.modules.ordering.repository;

import com.restaurante.sistema.modules.ordering.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUnitIdAndStatusIn(Long unitId, List<String> statuses);

    List<Order> findByCommandId(Long commandId);

    @Query("SELECT COALESCE(MAX(o.orderNumber), 0) FROM Order o WHERE o.unitId = :unitId")
    Long findMaxOrderNumberForUnit(@Param("unitId") Long unitId);
}
