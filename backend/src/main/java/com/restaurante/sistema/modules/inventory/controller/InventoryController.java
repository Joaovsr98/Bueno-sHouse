package com.restaurante.sistema.modules.inventory.controller;

import com.restaurante.sistema.config.Paging;
import org.springframework.http.ResponseEntity;
import com.restaurante.sistema.modules.inventory.dto.*;
import com.restaurante.sistema.modules.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/items")
    public ResponseEntity<List<InventoryItemResponse>> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return Paging.respond(inventoryService.listByUnit(unitId), page, size);
    }

    @GetMapping("/items/below-minimum")
    public List<InventoryItemResponse> belowMinimum(@RequestParam Long unitId) {
        return inventoryService.listBelowMinimum(unitId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.create(request);
    }

    @PostMapping("/items/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse registerMovement(@PathVariable Long id, @Valid @RequestBody StockMovementRequest request) {
        return inventoryService.registerManualMovement(id, request);
    }

    @GetMapping("/items/{id}/movements")
    public List<StockMovementResponse> listMovements(@PathVariable Long id) {
        return inventoryService.listMovements(id);
    }

    @PostMapping("/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse saveRecipe(@Valid @RequestBody RecipeRequest request) {
        return inventoryService.saveRecipe(request);
    }

    @GetMapping("/recipes/{productId}")
    public RecipeResponse getRecipeByProduct(@PathVariable Long productId) {
        return inventoryService.getRecipeByProduct(productId);
    }
}
