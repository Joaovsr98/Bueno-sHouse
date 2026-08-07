package com.restaurante.sistema.common.event;

/**
 * Publicado por KitchenService quando um OrderItem e marcado PRONTO.
 * Desacopla "a cozinha terminou este item" de "quem reage a isso" (hoje: baixa
 * automatica de estoque, InventoryEventListener) - o mecanismo de eventos de
 * dominio previsto desde a Etapa 2 e registrado como divida tecnica no
 * CONTINUAR-PROJETO.md ate esta implementacao (auditoria dos Capitulos 22/28
 * do material academico - Observer/Mediator).
 */
public record OrderItemReadyEvent(Long productId, Long orderItemId, int quantitySold) {
}
