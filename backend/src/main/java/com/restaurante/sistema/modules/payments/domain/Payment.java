package com.restaurante.sistema.modules.payments.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String method; // DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, VALE_REFEICAO, VALE_ALIMENTACAO, PAGAMENTO_ONLINE, OUTROS

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = "PENDENTE";

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    @Column(name = "cash_register_id")
    private Long cashRegisterId;

    @Column(name = "created_at")
    private Instant createdAt;
}
