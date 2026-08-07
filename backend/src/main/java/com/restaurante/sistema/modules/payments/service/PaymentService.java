package com.restaurante.sistema.modules.payments.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.cashregister.domain.CashRegister;
import com.restaurante.sistema.modules.cashregister.repository.CashRegisterRepository;
import com.restaurante.sistema.modules.dinein.domain.Command;
import com.restaurante.sistema.modules.dinein.repository.CommandRepository;
import com.restaurante.sistema.modules.dinein.service.CommandService;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.ordering.domain.Order;
import com.restaurante.sistema.modules.ordering.repository.OrderRepository;
import com.restaurante.sistema.modules.payments.domain.Payment;
import com.restaurante.sistema.modules.payments.dto.OrderBalanceResponse;
import com.restaurante.sistema.modules.payments.dto.PaymentResponse;
import com.restaurante.sistema.modules.payments.dto.RegisterPaymentRequest;
import com.restaurante.sistema.modules.payments.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 9 - modulo payments.
 *
 * Este service e onde a invariante central "uma comanda nao fecha com saldo
 * devedor" (documentada desde a Etapa 2) finalmente vira codigo de verdade:
 *
 *   - registerPayment soma o novo pagamento aos ja aprovados do pedido;
 *   - se o total pago == total do pedido, a Command associada e fechada
 *     automaticamente (via CommandService, respeitando a maquina de estado
 *     dela - primeiro AGUARDANDO_PAGAMENTO/PARCIALMENTE_PAGA, depois FECHADA);
 *   - se o total pago > total do pedido, a operacao e rejeitada (nunca aceita
 *     pagamento que "estoura" o valor devido - troco fica fora de escopo
 *     nesta etapa, e responsabilidade do caixa calcular fora do sistema por
 *     ora).
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CommandRepository commandRepository;
    private final CommandService commandService;
    private final CashRegisterRepository cashRegisterRepository;
    private final CurrentUserProvider currentUserProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            CommandRepository commandRepository,
            CommandService commandService,
            CashRegisterRepository cashRegisterRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.commandRepository = commandRepository;
        this.commandService = commandService;
        this.cashRegisterRepository = cashRegisterRepository;
        this.currentUserProvider = currentUserProvider;
    }

    private static final Set<String> NON_PAYABLE_ORDER_STATUSES = Set.of("CANCELADO", "FINALIZADO");
    private static final Set<String> COMMAND_OPEN_STATUSES = Set.of("ABERTA", "EM_ATENDIMENTO");

    @Transactional(readOnly = true)
    public OrderBalanceResponse getBalance(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        BigDecimal paid = paymentRepository.sumApprovedByOrderId(orderId);
        BigDecimal remaining = order.getTotal().subtract(paid);
        return new OrderBalanceResponse(orderId, order.getTotal(), paid, remaining, remaining.compareTo(BigDecimal.ZERO) <= 0);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PaymentResponse registerPayment(RegisterPaymentRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        if (NON_PAYABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(
                    "Nao e possivel registrar pagamento para um pedido " + order.getStatus());
        }

        Long cashRegisterId = null;
        if ("DINHEIRO".equals(request.method())) {
            if (request.cashRegisterId() == null) {
                throw new BusinessException("Pagamentos em dinheiro exigem um caixa aberto (cashRegisterId)");
            }
            CashRegister register = cashRegisterRepository.findById(request.cashRegisterId())
                    .orElseThrow(() -> new ResourceNotFoundException("CashRegister", request.cashRegisterId()));
            if (register.getClosedAt() != null) {
                throw new BusinessException("O caixa informado ja esta fechado");
            }
            cashRegisterId = register.getId();
        }

        BigDecimal alreadyPaid = paymentRepository.sumApprovedByOrderId(order.getId());
        BigDecimal totalAfter = alreadyPaid.add(request.amount());

        if (totalAfter.compareTo(order.getTotal()) > 0) {
            BigDecimal maxAllowed = order.getTotal().subtract(alreadyPaid);
            throw new BusinessException(
                    "Valor excede o saldo devedor do pedido. Maximo permitido: " + maxAllowed);
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setMethod(request.method());
        payment.setAmount(request.amount());
        payment.setStatus("APROVADO");
        payment.setRegisteredBy(currentUserProvider.getCurrentUserId());
        payment.setCashRegisterId(cashRegisterId);
        payment.setCreatedAt(Instant.now());

        Payment saved = paymentRepository.save(payment);

        boolean fullyPaid = totalAfter.compareTo(order.getTotal()) == 0;
        updateCommandPaymentStatus(order, fullyPaid);

        return toResponse(saved);
    }

    private void updateCommandPaymentStatus(Order order, boolean fullyPaid) {
        if (order.getCommandId() == null) {
            return; // pedidos fora do salao (BALCAO/RETIRADA) nao tem comanda
        }

        Command command = commandRepository.findById(order.getCommandId())
                .orElseThrow(() -> new ResourceNotFoundException("Command", order.getCommandId()));

        if (Set.of("FECHADA", "CANCELADA").contains(command.getStatus())) {
            return; // ja resolvida, nada a fazer
        }

        if (fullyPaid) {
            if (COMMAND_OPEN_STATUSES.contains(command.getStatus())) {
                commandService.transitionTo(command.getId(), "AGUARDANDO_PAGAMENTO");
            }
            String currentStatus = commandRepository.findById(command.getId()).orElseThrow().getStatus();
            if (!"FECHADA".equals(currentStatus)) {
                commandService.transitionTo(command.getId(), "FECHADA");
            }
        } else if (COMMAND_OPEN_STATUSES.contains(command.getStatus())) {
            commandService.transitionTo(command.getId(), "AGUARDANDO_PAGAMENTO");
            commandService.transitionTo(command.getId(), "PARCIALMENTE_PAGA");
        } else if ("AGUARDANDO_PAGAMENTO".equals(command.getStatus())) {
            commandService.transitionTo(command.getId(), "PARCIALMENTE_PAGA");
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getMethod(), p.getAmount(), p.getStatus(), p.getCreatedAt());
    }
}
