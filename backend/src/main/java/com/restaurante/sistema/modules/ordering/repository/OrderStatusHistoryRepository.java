package com.restaurante.sistema.modules.ordering.repository;

import com.restaurante.sistema.modules.ordering.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
}
