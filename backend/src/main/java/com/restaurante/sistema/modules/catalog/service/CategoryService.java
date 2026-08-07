package com.restaurante.sistema.modules.catalog.service;

import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.catalog.domain.Category;
import com.restaurante.sistema.modules.catalog.dto.CategoryRequest;
import com.restaurante.sistema.modules.catalog.dto.CategoryResponse;
import com.restaurante.sistema.modules.catalog.repository.CategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cardapio (categorias) e consultado com muito mais frequencia do que e
 * alterado (Capitulo 17 - Proxy/cache do material academico): as leituras
 * ficam em cache por unidade, e qualquer escrita invalida o cache inteiro
 * (allEntries=true) - simples e correto, dado o baixo volume de escritas de
 * catalogo comparado ao de leituras.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Cacheable(value = "categories", key = "#unitId")
    @Transactional(readOnly = true)
    public List<CategoryResponse> listByUnit(Long unitId) {
        return categoryRepository.findByUnitIdAndDeletedAtIsNullOrderByDisplayOrder(unitId)
                .stream().map(this::toResponse).toList();
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setUnitId(request.unitId());
        category.setName(request.name());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        return toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getOrThrow(id);
        category.setName(request.name());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        return toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public void softDelete(Long id) {
        Category category = getOrThrow(id);
        category.setDeletedAt(Instant.now());
        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getUnitId(), c.getName(), c.getDisplayOrder(), c.isActive());
    }
}
