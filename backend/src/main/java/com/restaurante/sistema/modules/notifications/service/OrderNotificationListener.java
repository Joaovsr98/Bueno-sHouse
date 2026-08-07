package com.restaurante.sistema.modules.notifications.service;

import com.restaurante.sistema.common.event.OrderReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reage a OrderReadyEvent notificando quem criou o pedido. OrderService, que
 * publica o evento, nao precisa conhecer NotificationService.
 */
@Component
public class OrderNotificationListener {

    private final NotificationService notificationService;

    public OrderNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onOrderReady(OrderReadyEvent event) {
        notificationService.notify(
                event.createdByUserId(),
                "Pedido pronto",
                "O pedido #" + event.orderNumber() + " esta pronto para entrega/retirada."
        );
    }
}
