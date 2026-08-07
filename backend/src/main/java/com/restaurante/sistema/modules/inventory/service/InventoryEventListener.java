package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.common.event.OrderItemReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reage a OrderItemReadyEvent baixando o estoque, quando o produto tiver
 * ficha tecnica cadastrada (InventoryService.deductForOrderItem ja trata o
 * caso de nao ter ficha - nao faz nada). KitchenService, que publica o
 * evento, nao precisa conhecer InventoryService.
 */
@Component
public class InventoryEventListener {

    private final InventoryService inventoryService;

    public InventoryEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onOrderItemReady(OrderItemReadyEvent event) {
        inventoryService.deductForOrderItem(event.productId(), event.orderItemId(), event.quantitySold());
    }
}
