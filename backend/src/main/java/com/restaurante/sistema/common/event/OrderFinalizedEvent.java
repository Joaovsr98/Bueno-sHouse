package com.restaurante.sistema.common.event;

/**
 * Publicado por OrderService quando um pedido e FINALIZADO. Hoje: o modulo de pagamentos
 * lanca o pagamento dos pedidos do site (delivery e retirada) no caixa aberto.
 */
public record OrderFinalizedEvent(Long orderId) {
}
