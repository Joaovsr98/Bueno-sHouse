package com.restaurante.sistema.modules.payments.service;

import com.restaurante.sistema.modules.cashregister.port.CashPayments;
import com.restaurante.sistema.modules.payments.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Implementa, no modulo de pagamentos, o total em dinheiro que o caixa confere no fechamento. */
@Component
public class CashPaymentsAdapter implements CashPayments {

    private final PaymentRepository paymentRepository;

    public CashPaymentsAdapter(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public BigDecimal approvedCashTotal(Long cashRegisterId) {
        return paymentRepository.sumApprovedCashByCashRegisterId(cashRegisterId);
    }
}
