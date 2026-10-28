package com.restaurante.sistema.modules.catalog.port;

/**
 * O que o catalogo precisa saber das fichas tecnicas (RN01: prato so fica ativo com
 * ficha). Implementada pelo modulo de estoque, que ja depende do catalogo; assim nao
 * ha ciclo catalog <-> inventory.
 */
public interface RecipeLookup {

    boolean hasRecipe(Long productId);
}
