package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;
}
