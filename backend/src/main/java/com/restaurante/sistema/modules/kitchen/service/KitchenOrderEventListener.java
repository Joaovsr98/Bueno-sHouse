package com.restaurante.sistema.modules.kitchen.service;

import com.restaurante.sistema.common.event.OrderItemSentToKitchenEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Repassa ao painel da cozinha (WebSocket) os itens que a confirmacao do pedido enviou. */
@Component
public class KitchenOrderEventListener {

    private final KitchenEventPublisher kitchenEventPublisher;

    public KitchenOrderEventListener(KitchenEventPublisher kitchenEventPublisher) {
        this.kitchenEventPublisher = kitchenEventPublisher;
    }

    @EventListener
    public void onItemSent(OrderItemSentToKitchenEvent event) {
        kitchenEventPublisher.publishItemEvent(event.unitId(), "ITEM_SENT", event.orderItemId(), event.orderId(), "ENVIADO");
    }
}
