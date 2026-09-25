package com.restaurante.sistema.modules.inventory.controller;

import com.restaurante.sistema.modules.inventory.dto.PurchaseRequest;
import com.restaurante.sistema.modules.inventory.dto.PurchaseResponse;
import com.restaurante.sistema.modules.inventory.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    public List<PurchaseResponse> list(@RequestParam Long unitId) {
        return purchaseService.listByUnit(unitId);
    }

    @GetMapping("/{id}")
    public PurchaseResponse findById(@PathVariable Long id) {
        return purchaseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse create(@Valid @RequestBody PurchaseRequest request) {
        return purchaseService.create(request);
    }

    @PatchMapping("/{id}/send")
    public PurchaseResponse send(@PathVariable Long id) {
        return purchaseService.send(id);
    }

    @PatchMapping("/{id}/cancel")
    public PurchaseResponse cancel(@PathVariable Long id) {
        return purchaseService.cancel(id);
    }

    @PostMapping("/{id}/receive")
    public PurchaseResponse receive(@PathVariable Long id) {
        return purchaseService.receive(id);
    }
}
