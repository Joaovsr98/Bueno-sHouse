package com.restaurante.sistema.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "recipe_items")
@Getter
@Setter
@NoArgsConstructor
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
}
