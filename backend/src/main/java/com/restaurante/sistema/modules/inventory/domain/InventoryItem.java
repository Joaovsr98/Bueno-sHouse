package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private String unitOfMeasure;

    @Column(name = "current_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(name = "minimum_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal minimumQuantity = BigDecimal.ZERO;

    @Column(name = "cost_per_unit", precision = 15, scale = 4)
    private BigDecimal costPerUnit = BigDecimal.ZERO;
}
