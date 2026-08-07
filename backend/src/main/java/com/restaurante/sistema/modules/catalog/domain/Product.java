package com.restaurante.sistema.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_sector_id")
    private KitchenSector kitchenSector;

    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private boolean featured = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariation> variations = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_additional_groups",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "additional_group_id")
    )
    private Set<AdditionalGroup> additionalGroups = new HashSet<>();
}
