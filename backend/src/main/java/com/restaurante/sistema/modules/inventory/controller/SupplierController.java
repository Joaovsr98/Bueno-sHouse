package com.restaurante.sistema.modules.inventory.controller;

import com.restaurante.sistema.config.Paging;
import com.restaurante.sistema.modules.inventory.dto.*;
import com.restaurante.sistema.modules.inventory.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return Paging.respond(supplierService.listByUnit(unitId), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @GetMapping("/{id}/products")
    public List<SupplierProductResponse> catalog(@PathVariable Long id) {
        return supplierService.listCatalog(id);
    }

    @PostMapping("/{id}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierProductResponse upsertProduct(@PathVariable Long id, @Valid @RequestBody SupplierProductRequest request) {
        return supplierService.upsertProduct(id, request);
    }

    /** Cotacao comparativa entre fornecedores para um ingrediente (RF-023). */
    @GetMapping("/quote")
    public List<QuoteResponse> quote(@RequestParam Long inventoryItemId) {
        return supplierService.compareQuotes(inventoryItemId);
    }
}
