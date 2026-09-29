package com.restaurante.sistema.modules.ordering.controller;

import com.restaurante.sistema.modules.customers.service.CustomerService;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
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
    private final CurrentUserProvider currentUserProvider;
    private final CustomerService customerService;

    public OrderController(
            OrderService orderService,
            CurrentUserProvider currentUserProvider,
            CustomerService customerService
    ) {
        this.orderService = orderService;
        this.currentUserProvider = currentUserProvider;
        this.customerService = customerService;
    }

    /**
     * Sem `page`/`size` devolve a lista completa (compativel com os paineis atuais).
     * Com `page` (0-based) e `size` (max 100) pagina e devolve o total em X-Total-Count.
     */
    @GetMapping
    public org.springframework.http.ResponseEntity<List<OrderResponse>> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        if (page == null) {
            return org.springframework.http.ResponseEntity.ok(orderService.listByUnit(unitId, status));
        }
        var result = orderService.listByUnitPaged(unitId, status, page, size);
        return org.springframework.http.ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    /** Pedidos do cliente logado (app do cliente). */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<OrderResponse> mine() {
        Long customerId = customerService.customerIdOfUser(currentUserProvider.getCurrentUserId());
        return orderService.listByCustomer(customerId);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        // CLIENTE so acessa o proprio pedido (checagem de propriedade); staff ve qualquer um.
        if ("CLIENTE".equals(currentUserProvider.getCurrentUser().getProfile().getName())) {
            Long customerId = customerService.customerIdOfUser(currentUserProvider.getCurrentUserId());
            return orderService.findByIdForCustomer(id, customerId);
        }
        return orderService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA','CLIENTE')")
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
