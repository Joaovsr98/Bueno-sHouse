package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variations")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "price_delta", nullable = false, precision = 15, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
