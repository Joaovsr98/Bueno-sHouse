package com.restaurante.sistema.modules.catalog.controller;

import com.restaurante.sistema.modules.catalog.dto.ProductRequest;
import com.restaurante.sistema.modules.catalog.dto.ProductResponse;
import com.restaurante.sistema.modules.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) Long categoryId
    ) {
        return categoryId != null
                ? productService.listByCategory(unitId, categoryId)
                : productService.listByUnit(unitId);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
