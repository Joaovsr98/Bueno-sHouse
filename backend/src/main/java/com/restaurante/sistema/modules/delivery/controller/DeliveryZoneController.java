package com.restaurante.sistema.modules.delivery.controller;

import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneRequest;
import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneResponse;
import com.restaurante.sistema.modules.delivery.service.DeliveryZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery-zones")
public class DeliveryZoneController {

    private final DeliveryZoneService deliveryZoneService;

    public DeliveryZoneController(DeliveryZoneService deliveryZoneService) {
        this.deliveryZoneService = deliveryZoneService;
    }

    @GetMapping
    public List<DeliveryZoneResponse> list(@RequestParam Long unitId) {
        return deliveryZoneService.listByUnit(unitId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryZoneResponse create(@Valid @RequestBody DeliveryZoneRequest request) {
        return deliveryZoneService.create(request);
    }
}
