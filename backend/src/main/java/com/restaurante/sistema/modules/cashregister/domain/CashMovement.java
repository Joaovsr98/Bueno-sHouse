package com.restaurante.sistema.modules.cashregister.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_movements")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_register_id", nullable = false)
    private CashRegister cashRegister;

    @Column(nullable = false, length = 20)
    private String type; // ENTRADA, SAIDA, SANGRIA, SUPRIMENTO

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    @Column(name = "created_at")
    private Instant createdAt;
}
