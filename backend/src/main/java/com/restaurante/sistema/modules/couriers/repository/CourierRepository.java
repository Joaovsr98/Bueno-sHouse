package com.restaurante.sistema.modules.couriers.repository;

import com.restaurante.sistema.modules.couriers.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, Long> {
    List<Courier> findByUnitIdAndStatus(Long unitId, String status);
    Optional<Courier> findByUserId(Long userId);
}
