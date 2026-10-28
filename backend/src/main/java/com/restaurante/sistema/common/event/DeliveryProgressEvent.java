package com.restaurante.sistema.common.event;

/**
 * Publicado por DeliveryService quando a entrega avanca: o motoboy saiu (LEFT) ou a
 * entrega foi confirmada com o codigo (DELIVERED). O modulo de pedidos escuta e leva
 * o pedido junto (SAIU_PARA_ENTREGA / ENTREGUE -> FINALIZADO) na mesma transacao -
 * assim delivery nao depende de ordering (e ordering -> delivery continua o unico sentido).
 */
public record DeliveryProgressEvent(Long orderId, Stage stage) {

    public enum Stage { LEFT, DELIVERED }
}
