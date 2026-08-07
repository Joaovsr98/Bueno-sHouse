package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "additionals")
@Getter
@Setter
@NoArgsConstructor
public class Additional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "additional_group_id", nullable = false)
    private AdditionalGroup additionalGroup;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
