package com.restaurante.sistema.modules.customers.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(length = 40)
    private String label;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    @JdbcTypeCode(SqlTypes.CHAR) // coluna CHAR no banco (Flyway); sem isto o validate do Hibernate falha
    private String state;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(name = "reference_point", length = 200)
    private String referencePoint;

    /** Localizacao buscada pelo servidor no OpenStreetMap (null = nao achou ou ainda nao buscou). */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Quando a busca da localizacao teve resposta; null = ainda vai buscar. */
    @Column(name = "geocoded_at")
    private Instant geocodedAt;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
