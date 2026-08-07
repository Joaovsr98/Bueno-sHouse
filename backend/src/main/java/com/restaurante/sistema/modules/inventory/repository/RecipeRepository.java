package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Optional<Recipe> findByProductId(Long productId);
}
