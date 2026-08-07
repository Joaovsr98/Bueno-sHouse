package com.restaurante.sistema.modules.ordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item_additionals")
@Getter
@Setter
@NoArgsConstructor
public class OrderItemAdditional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "additional_id", nullable = false)
    private Long additionalId;

    @Column(name = "additional_name_snapshot", nullable = false, length = 80)
    private String additionalNameSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(nullable = false)
    private int quantity = 1;
}
