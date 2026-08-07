package com.restaurante.sistema.modules.dinein.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "commands")
@Getter
@Setter
@NoArgsConstructor
public class Command {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(nullable = false, length = 30)
    private String status = "ABERTA";

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
