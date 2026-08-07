package com.restaurante.sistema.modules.catalog.service;

import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.catalog.domain.*;
import com.restaurante.sistema.modules.catalog.dto.*;
import com.restaurante.sistema.modules.catalog.repository.AdditionalGroupRepository;
import com.restaurante.sistema.modules.catalog.repository.CategoryRepository;
import com.restaurante.sistema.modules.catalog.repository.KitchenSectorRepository;
import com.restaurante.sistema.modules.catalog.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Etapa 6.
 *
 * Nota sobre precos: base_price e price_delta (das variacoes) sao definidos
 * aqui, no catalogo. A regra critica do projeto - "backend nunca confia no
 * preco enviado pelo frontend" - se aplica ao MODULO ORDERING (Etapa 8), que
 * buscara o preco vigente aqui no momento do pedido e o congelara em
 * order_items. Este service e o unico lugar que define/altera o preco.
 *
 * Nota sobre variacoes na atualizacao: por simplicidade nesta etapa, a lista
 * de variacoes enviada substitui integralmente a lista anterior (remove +
 * recria). Como o modulo ordering ainda nao existe, nao ha risco de quebrar
 * referencia historica de pedidos. Quando o modulo ordering for implementado,
 * esta estrategia sera revisada para preservar IDs referenciados por pedidos
 * antigos (order_items.variation_id usa ON DELETE RESTRICT, entao a troca
 * completa deixara de ser possivel assim que houver pedidos reais).
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final KitchenSectorRepository kitchenSectorRepository;
    private final AdditionalGroupRepository additionalGroupRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            KitchenSectorRepository kitchenSectorRepository,
            AdditionalGroupRepository additionalGroupRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.kitchenSectorRepository = kitchenSectorRepository;
        this.additionalGroupRepository = additionalGroupRepository;
    }

    @Cacheable(value = "products", key = "#unitId")
    @Transactional(readOnly = true)
    public List<ProductResponse> listByUnit(Long unitId) {
        return productRepository.findByUnitIdAndDeletedAtIsNull(unitId).stream().map(this::toResponse).toList();
    }

    @Cacheable(value = "products", key = "#unitId + '-' + #categoryId")
    @Transactional(readOnly = true)
    public List<ProductResponse> listByCategory(Long unitId, Long categoryId) {
        return productRepository.findByUnitIdAndCategoryIdAndDeletedAtIsNull(unitId, categoryId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setUnitId(request.unitId());
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getOrThrow(id);
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public void softDelete(Long id) {
        Product product = getOrThrow(id);
        product.setDeletedAt(Instant.now());
        product.setAvailable(false);
        productRepository.save(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        product.setCategory(category);

        product.setName(request.name());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setImageUrl(request.imageUrl());
        product.setPrepTimeMinutes(request.prepTimeMinutes());
        product.setAvailable(request.available() == null || request.available());
        product.setFeatured(request.featured() != null && request.featured());

        if (request.kitchenSectorId() != null) {
            KitchenSector sector = kitchenSectorRepository.findById(request.kitchenSectorId())
                    .orElseThrow(() -> new ResourceNotFoundException("KitchenSector", request.kitchenSectorId()));
            product.setKitchenSector(sector);
        } else {
            product.setKitchenSector(null);
        }

        product.getVariations().clear();
        if (request.variations() != null) {
            request.variations().forEach(v -> {
                ProductVariation variation = new ProductVariation();
                variation.setProduct(product);
                variation.setName(v.name());
                variation.setPriceDelta(v.priceDelta());
                product.getVariations().add(variation);
            });
        }

        if (request.additionalGroupIds() != null) {
            Set<AdditionalGroup> groups = new HashSet<>(additionalGroupRepository.findAllById(request.additionalGroupIds()));
            product.setAdditionalGroups(groups);
        }
    }

    private Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private ProductResponse toResponse(Product p) {
        List<ProductVariationResponse> variations = p.getVariations().stream()
                .map(v -> new ProductVariationResponse(v.getId(), v.getName(), v.getPriceDelta(), v.isActive()))
                .toList();

        Set<Long> groupIds = p.getAdditionalGroups().stream().map(AdditionalGroup::getId).collect(Collectors.toSet());

        return new ProductResponse(
                p.getId(),
                p.getUnitId(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getName(),
                p.getDescription(),
                p.getBasePrice(),
                p.getImageUrl(),
                p.getPrepTimeMinutes(),
                p.getKitchenSector() != null ? p.getKitchenSector().getId() : null,
                p.isAvailable(),
                p.isFeatured(),
                variations,
                groupIds
        );
    }
}
