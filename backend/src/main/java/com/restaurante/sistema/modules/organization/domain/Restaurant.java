package com.restaurante.sistema.modules.organization.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "trade_name", nullable = false, length = 150)
    private String tradeName;

    @Column(nullable = false, unique = true, length = 20)
    private String document;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
