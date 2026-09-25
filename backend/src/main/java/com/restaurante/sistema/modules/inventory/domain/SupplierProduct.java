package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Catalogo de um fornecedor: quanto ele cobra por um ingrediente. RF-022/RF-023. */
@Entity
@Table(name = "supplier_products")
@Getter
@Setter
@NoArgsConstructor
public class SupplierProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private String unitOfMeasure;
}
