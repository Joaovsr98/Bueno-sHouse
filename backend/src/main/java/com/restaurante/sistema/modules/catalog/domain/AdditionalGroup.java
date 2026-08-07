package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "additional_groups")
@Getter
@Setter
@NoArgsConstructor
public class AdditionalGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity = 0;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity = 1;

    @Column(nullable = false)
    private boolean required = false;

    @OneToMany(mappedBy = "additionalGroup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Additional> additionals = new ArrayList<>();
}
