package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modulo kitchen sera desenvolvido completo na Etapa 8 (junto com o painel
 * em tempo real). Esta entidade minima existe aqui porque "products" ja
 * referencia setor de producao desde a Etapa 6 (catalogo).
 */
@Entity
@Table(name = "kitchen_sectors")
@Getter
@Setter
@NoArgsConstructor
public class KitchenSector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false, length = 60)
    private String name;
}
