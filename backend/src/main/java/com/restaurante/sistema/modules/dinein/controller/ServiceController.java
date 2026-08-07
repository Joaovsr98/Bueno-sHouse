package com.restaurante.sistema.modules.dinein.controller;

import com.restaurante.sistema.modules.dinein.dto.CancelRequest;
import com.restaurante.sistema.modules.dinein.dto.OpenServiceRequest;
import com.restaurante.sistema.modules.dinein.dto.ServiceResponse;
import com.restaurante.sistema.modules.dinein.dto.TransitionRequest;
import com.restaurante.sistema.modules.dinein.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints do Atendimento (Service). Operado principalmente pelo garcom.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public List<ServiceResponse> history(@RequestParam Long tableId) {
        return serviceService.history(tableId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM')")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse open(@Valid @RequestBody OpenServiceRequest request) {
        return serviceService.open(request);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM')")
    public ServiceResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return serviceService.transitionTo(id, request.status());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ServiceResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return serviceService.cancel(id, request.reason());
    }
}
