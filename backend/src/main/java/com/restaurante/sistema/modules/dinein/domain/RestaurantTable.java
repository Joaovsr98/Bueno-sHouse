package com.restaurante.sistema.modules.dinein.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tables")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 10)
    private String number;

    @Column(nullable = false)
    private int capacity = 4;

    @Column(nullable = false, length = 20)
    private String status = "LIVRE";

    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
