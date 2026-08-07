package com.restaurante.sistema.modules.organization.repository;

import com.restaurante.sistema.modules.organization.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByRestaurantId(Long restaurantId);
    List<Unit> findByActiveTrue();
}
