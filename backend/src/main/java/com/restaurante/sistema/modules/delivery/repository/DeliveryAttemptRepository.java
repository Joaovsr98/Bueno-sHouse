package com.restaurante.sistema.modules.delivery.repository;

import com.restaurante.sistema.modules.delivery.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
}
