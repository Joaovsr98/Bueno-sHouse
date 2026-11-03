package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.modules.catalog.port.RecipeLookup;
import com.restaurante.sistema.modules.inventory.repository.RecipeRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Implementa, no modulo de estoque, a consulta de ficha tecnica que o catalogo usa. */
@Component
public class RecipeLookupAdapter implements RecipeLookup {

    private final RecipeRepository recipeRepository;
    private final InventoryService inventoryService;

    public RecipeLookupAdapter(RecipeRepository recipeRepository, @Lazy InventoryService inventoryService) {
        this.recipeRepository = recipeRepository;
        this.inventoryService = inventoryService;
    }

    /** Mesma conta da criacao do pedido (missingIngredients) para uma porcao de cada produto. */
    @Override
    public Set<Long> outOfStock(Collection<Long> productIds) {
        return productIds.stream()
                .filter(id -> !inventoryService.missingIngredients(Map.of(id, 1)).isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasRecipe(Long productId) {
        return productId != null && recipeRepository.findByProductId(productId).isPresent();
    }
}
