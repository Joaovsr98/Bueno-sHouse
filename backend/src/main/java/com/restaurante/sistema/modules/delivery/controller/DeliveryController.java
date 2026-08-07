package com.restaurante.sistema.modules.delivery.controller;

import com.restaurante.sistema.modules.delivery.domain.Delivery;
import com.restaurante.sistema.modules.delivery.dto.*;
import com.restaurante.sistema.modules.delivery.repository.DeliveryRepository;
import com.restaurante.sistema.modules.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryRepository deliveryRepository;

    public DeliveryController(DeliveryService deliveryService, DeliveryRepository deliveryRepository) {
        this.deliveryService = deliveryService;
        this.deliveryRepository = deliveryRepository;
    }

    /** Pool de entregas aguardando entregador - qualquer MOTOBOY pode ver e tentar aceitar. */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','MOTOBOY')")
    public List<Long> listAvailable() {
        return deliveryRepository.findByStatus("AGUARDANDO_ENTREGADOR").stream().map(Delivery::getId).toList();
    }

    @GetMapping("/by-order/{orderId}")
    public DeliveryResponse findByOrder(@PathVariable Long orderId) {
        return deliveryService.findByOrderId(orderId);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('MOTOBOY')")
    public DeliveryResponse accept(@PathVariable Long id) {
        return deliveryService.accept(id);
    }

    @PostMapping("/{id}/pickup")
    @PreAuthorize("hasAnyRole('MOTOBOY','ADMINISTRADOR','GERENTE')")
    public DeliveryResponse pickup(@PathVariable Long id) {
        return deliveryService.pickup(id);
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("hasAnyRole('MOTOBOY','ADMINISTRADOR','GERENTE')")
    public DeliveryResponse leave(@PathVariable Long id) {
        return deliveryService.leaveForDelivery(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('MOTOBOY','ADMINISTRADOR','GERENTE')")
    public DeliveryResponse confirm(@PathVariable Long id, @Valid @RequestBody ConfirmDeliveryRequest request) {
        return deliveryService.confirmDelivery(id, request);
    }

    @PostMapping("/{id}/failure")
    @PreAuthorize("hasAnyRole('MOTOBOY','ADMINISTRADOR','GERENTE')")
    public DeliveryResponse reportFailure(@PathVariable Long id, @Valid @RequestBody DeliveryFailureRequest request) {
        return deliveryService.reportFailure(id, request);
    }
}
