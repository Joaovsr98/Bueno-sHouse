package com.restaurante.sistema.modules.ordering.controller;

import com.restaurante.sistema.modules.ordering.dto.*;
import com.restaurante.sistema.modules.ordering.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) List<String> status
    ) {
        return orderService.listByUnit(unitId, status);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @PostMapping("/{id}/send-to-kitchen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')")
    public OrderResponse sendToKitchen(@PathVariable Long id) {
        return orderService.sendToKitchen(id);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')")
    public OrderResponse transition(@PathVariable Long id, @Valid @RequestBody OrderTransitionRequest request) {
        return orderService.transitionTo(id, request.status(), request.reason());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody OrderTransitionRequest request) {
        return orderService.cancel(id, request.reason());
    }
}
