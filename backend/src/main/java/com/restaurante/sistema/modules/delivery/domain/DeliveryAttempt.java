package com.restaurante.sistema.modules.delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(length = 255)
    private String reason;

    @Column(name = "next_action", length = 255)
    private String nextAction;
}
