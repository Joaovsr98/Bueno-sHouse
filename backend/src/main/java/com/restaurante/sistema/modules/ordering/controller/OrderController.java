package com.restaurante.sistema.modules.ordering.controller;

import com.restaurante.sistema.modules.customers.service.CustomerService;
import com.restaurante.sistema.modules.delivery.service.DeliveryService;
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
    private final DeliveryService deliveryService;

    public OrderController(
            OrderService orderService,
            CurrentUserProvider currentUserProvider,
            CustomerService customerService,
            DeliveryService deliveryService
    ) {
        this.orderService = orderService;
        this.currentUserProvider = currentUserProvider;
        this.customerService = customerService;
        this.deliveryService = deliveryService;
    }

    /**
     * Sem `page`/`size` devolve a lista completa (compativel com os paineis atuais).
     * Com `page` (0-based) e `size` (max 100) pagina e devolve o total em X-Total-Count.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA','COZINHA')")
    public org.springframework.http.ResponseEntity<List<OrderResponse>> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        if (page == null) {
            return org.springframework.http.ResponseEntity.ok(orderService.listByUnit(unitId, status));
        }
        var result = orderService.listByUnitPaged(unitId, status, channel, from, to, page, size);
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

    /**
     * Codigo de confirmacao da entrega, para o cliente informar ao motoboy.
     * So o dono do pedido recebe (findByIdForCustomer devolve 404 para outros).
     */
    @GetMapping("/{id}/delivery-code")
    @PreAuthorize("hasRole('CLIENTE')")
    public java.util.Map<String, String> deliveryCode(@PathVariable Long id) {
        Long customerId = customerService.customerIdOfUser(currentUserProvider.getCurrentUserId());
        orderService.findByIdForCustomer(id, customerId);
        return java.util.Map.of("confirmationCode", deliveryService.confirmationCodeForOrder(id));
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
        // RN04: cancelar apos EM_PREPARO so GERENTE/ADMIN (antes disso, qualquer perfil do endpoint).
        if ("CANCELADO".equals(request.status())) {
            String profile = currentUserProvider.getCurrentUser().getProfile().getName();
            boolean manager = "ADMINISTRADOR".equals(profile) || "GERENTE".equals(profile);
            if (!manager && !orderService.canCancelFreely(id)) {
                throw new com.restaurante.sistema.common.exception.BusinessException(
                        "Pedido ja entrou em producao: so GERENTE ou ADMINISTRADOR podem cancelar");
            }
        }
        return orderService.transitionTo(id, request.status(), request.reason());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody OrderTransitionRequest request) {
        return orderService.cancel(id, request.reason());
    }
}
