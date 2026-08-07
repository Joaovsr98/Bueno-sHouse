package com.restaurante.sistema.common.event;

/**
 * Publicado por OrderService quando um Order fica PRONTO (todos os itens
 * concluidos). Desacopla "o pedido ficou pronto" de "quem reage a isso" (hoje:
 * notificar quem criou o pedido, OrderNotificationListener).
 */
public record OrderReadyEvent(Long orderId, Long orderNumber, Long createdByUserId) {
}
