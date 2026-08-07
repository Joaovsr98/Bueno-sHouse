package com.restaurante.sistema.modules.delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "courier_id")
    private Long courierId;

    @Column(name = "delivery_zone_id")
    private Long deliveryZoneId;

    @Column(name = "address_snapshot", nullable = false, length = 500)
    private String addressSnapshot;

    @Column(name = "neighborhood_snapshot", nullable = false, length = 100)
    private String neighborhoodSnapshot;

    @Column(name = "reference_point_snapshot", length = 200)
    private String referencePointSnapshot;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(nullable = false, length = 30)
    private String status = "AGUARDANDO_ENTREGADOR";

    @Column(name = "confirmation_code", nullable = false, length = 10)
    private String confirmationCode;

    @Column(name = "received_by_name", length = 150)
    private String receivedByName;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
