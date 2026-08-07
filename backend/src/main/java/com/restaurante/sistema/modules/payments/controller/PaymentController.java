package com.restaurante.sistema.modules.payments.controller;

import com.restaurante.sistema.modules.payments.dto.OrderBalanceResponse;
import com.restaurante.sistema.modules.payments.dto.PaymentResponse;
import com.restaurante.sistema.modules.payments.dto.RegisterPaymentRequest;
import com.restaurante.sistema.modules.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','CAIXA')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> listByOrder(@RequestParam Long orderId) {
        return paymentService.listByOrder(orderId);
    }

    @GetMapping("/balance")
    public OrderBalanceResponse balance(@RequestParam Long orderId) {
        return paymentService.getBalance(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse register(@Valid @RequestBody RegisterPaymentRequest request) {
        return paymentService.registerPayment(request);
    }
}
