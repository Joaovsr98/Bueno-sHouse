package com.restaurante.sistema.modules.organization.repository;

import com.restaurante.sistema.modules.organization.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
}
