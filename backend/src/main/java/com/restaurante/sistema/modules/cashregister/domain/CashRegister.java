package com.restaurante.sistema.modules.cashregister.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cash_registers")
@Getter
@Setter
@NoArgsConstructor
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 15, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "expected_balance", precision = 15, scale = 2)
    private BigDecimal expectedBalance;

    @Column(precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "open_flag", insertable = false, updatable = false)
    private Integer openFlag;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "cashRegister", fetch = FetchType.LAZY)
    private List<CashMovement> movements = new ArrayList<>();
}
