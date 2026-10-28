package com.restaurante.sistema.common.event;

/**
 * Publicado quando um OrderItem e cancelado depois de o pedido ser confirmado
 * (ex.: a cozinha marca o item como indisponivel). Quem baixou estoque na
 * confirmacao reage estornando (InventoryEventListener) - a cozinha nao
 * precisa conhecer o modulo de estoque.
 */
public record OrderItemCancelledEvent(Long orderItemId, String reason) {
}
