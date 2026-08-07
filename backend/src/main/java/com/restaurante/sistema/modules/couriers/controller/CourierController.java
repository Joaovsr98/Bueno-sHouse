package com.restaurante.sistema.modules.couriers.controller;

import com.restaurante.sistema.modules.couriers.dto.CourierRequest;
import com.restaurante.sistema.modules.couriers.dto.CourierResponse;
import com.restaurante.sistema.modules.couriers.dto.CourierStatusRequest;
import com.restaurante.sistema.modules.couriers.service.CourierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @GetMapping("/available")
    public List<CourierResponse> listAvailable(@RequestParam Long unitId) {
        return courierService.listAvailable(unitId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@Valid @RequestBody CourierRequest request) {
        return courierService.create(request);
    }

    /**
     * O proprio entregador atualiza seu status (ex.: ficar DISPONIVEL ao
     * iniciar o turno). Gerente/administrador tambem podem, para casos como
     * bloquear um entregador.
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','MOTOBOY')")
    public CourierResponse changeStatus(@PathVariable Long id, @Valid @RequestBody CourierStatusRequest request) {
        return courierService.changeStatus(id, request.status());
    }
}
