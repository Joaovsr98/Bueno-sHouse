package com.restaurante.sistema.modules.delivery.repository;

import com.restaurante.sistema.modules.delivery.domain.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findByStatus(String status);
    List<Delivery> findByCourierIdAndStatusIn(Long courierId, List<String> statuses);
}
