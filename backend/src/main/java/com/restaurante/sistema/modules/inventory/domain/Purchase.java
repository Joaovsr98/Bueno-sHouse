package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "purchased_at", nullable = false)
    private LocalDate purchasedAt;
}
