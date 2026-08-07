package com.restaurante.sistema.modules.dinein.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Atendimento (Service): periodo em que uma mesa esta ocupada por um grupo de
 * clientes. Ver glossario na Etapa 2 - nao confundir com "servico" no sentido
 * de item vendido.
 *
 * O campo active_flag e calculado pelo MySQL (coluna gerada STORED, ver
 * migration V6) e garante via indice unico que uma mesa nao tenha dois
 * atendimentos ativos simultaneamente. Aqui ele e mapeado como somente
 * leitura (insertable/updatable = false) - a aplicacao nunca escreve nele
 * diretamente, apenas o MySQL o recalcula a partir do "status".
 */
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(name = "party_size", nullable = false)
    private int partySize = 1;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(length = 300)
    private String notes;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "active_flag", insertable = false, updatable = false)
    private Integer activeFlag;

    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
