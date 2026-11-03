package com.restaurante.sistema.modules.payments.service;

import com.restaurante.sistema.common.event.OrderFinalizedEvent;
import com.restaurante.sistema.modules.cashregister.domain.CashRegister;
import com.restaurante.sistema.modules.cashregister.repository.CashRegisterRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.ordering.domain.Order;
import com.restaurante.sistema.modules.ordering.repository.OrderRepository;
import com.restaurante.sistema.modules.payments.domain.Payment;
import com.restaurante.sistema.modules.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Pedido do site (delivery ou retirada) finalizado: lanca o pagamento na forma que o cliente
 * escolheu, no caixa aberto da unidade. Assim o dinheiro que o motoboy recebe na porta entra
 * no saldo esperado do fechamento, e o faturamento do dashboard bate com os pagamentos.
 * Sincrono, na mesma transacao da finalizacao; quem registra e quem finalizou (o motoboy na
 * entrega, a equipe na retirada).
 */
@Component
public class SiteOrderPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(SiteOrderPaymentListener.class);
    private static final Set<String> SITE_CHANNELS = Set.of("DELIVERY", "RETIRADA");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final CurrentUserProvider currentUserProvider;

    public SiteOrderPaymentListener(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                    CashRegisterRepository cashRegisterRepository, CurrentUserProvider currentUserProvider) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @EventListener
    public void onOrderFinalized(OrderFinalizedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getPaymentMethod() == null || !SITE_CHANNELS.contains(order.getChannel())) return;

        BigDecimal remaining = order.getTotal().subtract(paymentRepository.sumApprovedByOrderId(order.getId()));
        if (remaining.signum() <= 0) return; // ja pago no caixa

        Long cashRegisterId = cashRegisterRepository.findByUnitIdAndOpenFlag(order.getUnitId(), 1)
                .map(CashRegister::getId).orElse(null);
        if (cashRegisterId == null && "DINHEIRO".equals(order.getPaymentMethod())) {
            log.warn("Pedido {} pago em dinheiro sem caixa aberto: pagamento registrado fora do caixa", order.getId());
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setMethod(order.getPaymentMethod());
        payment.setAmount(remaining);
        payment.setStatus("APROVADO");
        payment.setRegisteredBy(currentUserProvider.getCurrentUserId());
        payment.setCashRegisterId(cashRegisterId);
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);
    }
}
