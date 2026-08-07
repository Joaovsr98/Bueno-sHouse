package com.restaurante.sistema.modules.delivery.repository;

import com.restaurante.sistema.modules.delivery.domain.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {
    List<DeliveryZone> findByUnitIdAndActiveTrue(Long unitId);
    Optional<DeliveryZone> findByUnitIdAndNeighborhoodAndActiveTrue(Long unitId, String neighborhood);
}
