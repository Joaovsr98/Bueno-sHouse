package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(nullable = false, length = 20)
    private String type; // ENTRADA_COMPRA, SAIDA_PRODUCAO, AJUSTE_PERDA, ESTORNO_CANCELAMENTO

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "reference_order_item_id")
    private Long referenceOrderItemId;

    @Column(length = 255)
    private String reason;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    @Column(name = "created_at")
    private Instant createdAt;
}
