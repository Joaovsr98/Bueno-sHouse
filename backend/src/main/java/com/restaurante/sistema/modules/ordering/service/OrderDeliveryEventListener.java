package com.restaurante.sistema.modules.ordering.service;

import com.restaurante.sistema.common.event.DeliveryProgressEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Acompanha a entrega: quando o motoboy sai, o pedido vai para SAIU_PARA_ENTREGA;
 * quando a entrega e confirmada, ENTREGUE e depois FINALIZADO. Sincrono, na mesma
 * transacao da entrega: se a transicao do pedido for invalida, a da entrega e desfeita.
 */
@Component
public class OrderDeliveryEventListener {

    private final OrderService orderService;

    public OrderDeliveryEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @EventListener
    public void onDeliveryProgress(DeliveryProgressEvent event) {
        switch (event.stage()) {
            case LEFT -> orderService.transitionTo(event.orderId(), "SAIU_PARA_ENTREGA", null);
            case DELIVERED -> {
                orderService.transitionTo(event.orderId(), "ENTREGUE", null);
                orderService.transitionTo(event.orderId(), "FINALIZADO", null);
            }
        }
    }
}
