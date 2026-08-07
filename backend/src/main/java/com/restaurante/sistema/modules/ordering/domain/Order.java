package com.restaurante.sistema.modules.ordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId;

    @Column(name = "order_number", nullable = false)
    private Long orderNumber;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 20)
    private String channel; // SALAO, BALCAO, RETIRADA, DELIVERY

    @Column(name = "command_id")
    private Long commandId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 30)
    private String status = "RASCUNHO";

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "service_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
    }
}
