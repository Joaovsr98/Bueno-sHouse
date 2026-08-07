package com.restaurante.sistema.modules.kitchen.controller;

import com.restaurante.sistema.modules.kitchen.dto.KitchenTaskResponse;
import com.restaurante.sistema.modules.kitchen.dto.UnavailableRequest;
import com.restaurante.sistema.modules.kitchen.service.KitchenService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints do painel da cozinha. Operado principalmente pelo perfil COZINHA,
 * mas ADMINISTRADOR/GERENTE tambem podem intervir (ex.: destravar um item
 * preso por engano).
 */
@RestController
@RequestMapping("/api/kitchen")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','COZINHA')")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/tasks")
    public List<KitchenTaskResponse> listTasks(
            @RequestParam Long sectorId,
            @RequestParam(required = false) List<String> status
    ) {
        return kitchenService.listTasks(sectorId, status);
    }

    @PostMapping("/tasks/{orderItemId}/start")
    public KitchenTaskResponse start(@PathVariable Long orderItemId) {
        return kitchenService.start(orderItemId);
    }

    @PostMapping("/tasks/{orderItemId}/complete")
    public KitchenTaskResponse complete(@PathVariable Long orderItemId) {
        return kitchenService.complete(orderItemId);
    }

    @PostMapping("/tasks/{orderItemId}/unavailable")
    public KitchenTaskResponse markUnavailable(
            @PathVariable Long orderItemId,
            @Valid @RequestBody UnavailableRequest request
    ) {
        return kitchenService.markUnavailable(orderItemId, request.reason());
    }
}
