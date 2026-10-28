package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.modules.catalog.port.RecipeLookup;
import com.restaurante.sistema.modules.inventory.repository.RecipeRepository;
import org.springframework.stereotype.Component;

/** Implementa, no modulo de estoque, a consulta de ficha tecnica que o catalogo usa. */
@Component
public class RecipeLookupAdapter implements RecipeLookup {

    private final RecipeRepository recipeRepository;

    public RecipeLookupAdapter(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public boolean hasRecipe(Long productId) {
        return productId != null && recipeRepository.findByProductId(productId).isPresent();
    }
}
