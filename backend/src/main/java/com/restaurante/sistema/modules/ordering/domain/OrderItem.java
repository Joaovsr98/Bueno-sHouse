package com.restaurante.sistema.modules.ordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 120)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 300)
    private String notes;

    @Column(name = "variation_id")
    private Long variationId;

    @Column(nullable = false, length = 20)
    private String status = "PENDENTE";

    @Column(name = "kitchen_sector_id")
    private Long kitchenSectorId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "produced_by")
    private Long producedBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemAdditional> additionals = new ArrayList<>();
}
