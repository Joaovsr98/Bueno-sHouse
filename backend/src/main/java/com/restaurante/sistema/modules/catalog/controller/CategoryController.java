package com.restaurante.sistema.modules.catalog.controller;

import com.restaurante.sistema.modules.catalog.dto.CategoryRequest;
import com.restaurante.sistema.modules.catalog.dto.CategoryResponse;
import com.restaurante.sistema.modules.catalog.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Leitura: qualquer usuario autenticado (garcom e cozinha tambem precisam
 * ver o cardapio). Escrita: ADMINISTRADOR ou GERENTE.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam Long unitId) {
        return categoryService.listByUnit(unitId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
