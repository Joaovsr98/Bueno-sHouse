package com.restaurante.sistema.modules.kitchen.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Publica eventos no topico STOMP "/topic/kitchen/{unitId}" sempre que um
 * item de pedido muda de status. O painel da cozinha (frontend, Etapa 9+)
 * se inscreve nesse topico para atualizar em tempo real sem polling.
 */
@Component
public class KitchenEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public KitchenEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishItemEvent(Long unitId, String eventType, Long orderItemId, Long orderId, String newStatus) {
        Map<String, Object> payload = Map.of(
                "eventType", eventType, // ex.: "ITEM_SENT", "ITEM_STARTED", "ITEM_READY", "ITEM_CANCELLED"
                "orderItemId", orderItemId,
                "orderId", orderId,
                "status", newStatus,
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/kitchen/" + unitId, payload);
    }
}
