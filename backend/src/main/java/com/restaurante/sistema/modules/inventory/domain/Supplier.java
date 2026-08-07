package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String document;
}
