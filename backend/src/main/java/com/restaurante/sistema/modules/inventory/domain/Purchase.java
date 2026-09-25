package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pedido de compra. Ciclo: RASCUNHO -> ENVIADO -> RECEBIDO (ou CANCELADO). RF-024/025. */
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
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(nullable = false, length = 20)
    private String status = "RASCUNHO";

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseItem> items = new ArrayList<>();
}
