package com.restaurante.sistema.modules.couriers.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "couriers")
@Getter
@Setter
@NoArgsConstructor
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 20)
    private String document;

    @Column(name = "vehicle_type", length = 30)
    private String vehicleType;

    @Column(length = 10)
    private String plate;

    @Column(nullable = false, length = 20)
    private String status = "OFFLINE";

    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
