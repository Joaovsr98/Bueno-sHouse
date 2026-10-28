package com.restaurante.sistema.common.event;

/**
 * Publicado por OrderService quando a confirmacao envia um item para a cozinha.
 * O modulo kitchen escuta e avisa o painel em tempo real (WebSocket) - assim o
 * modulo de pedidos nao depende do de cozinha (a cozinha e que depende de pedidos).
 */
public record OrderItemSentToKitchenEvent(Long unitId, Long orderItemId, Long orderId) {
}
