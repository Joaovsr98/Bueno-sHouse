package com.restaurante.sistema.modules.delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_zones")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(name = "minimum_order_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumOrderValue = BigDecimal.ZERO;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(nullable = false)
    private boolean active = true;
}
