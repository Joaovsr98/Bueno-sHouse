package com.restaurante.sistema.modules.dinein.repository;

import com.restaurante.sistema.modules.dinein.domain.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findByUnitId(Long unitId);
}
