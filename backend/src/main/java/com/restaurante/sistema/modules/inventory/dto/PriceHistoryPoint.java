package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** RF-026: preco pago por um ingrediente a um fornecedor numa compra recebida. */
public record PriceHistoryPoint(Long inventoryItemId, String itemName, LocalDate date, BigDecimal unitPrice) {}
