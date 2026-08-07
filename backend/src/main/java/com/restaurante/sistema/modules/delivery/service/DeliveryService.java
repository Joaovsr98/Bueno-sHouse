package com.restaurante.sistema.modules.delivery.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ConcurrencyConflictException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.couriers.domain.Courier;
import com.restaurante.sistema.modules.couriers.repository.CourierRepository;
import com.restaurante.sistema.modules.delivery.domain.Delivery;
import com.restaurante.sistema.modules.delivery.domain.DeliveryAttempt;
import com.restaurante.sistema.modules.delivery.dto.*;
import com.restaurante.sistema.modules.delivery.repository.DeliveryAttemptRepository;
import com.restaurante.sistema.modules.delivery.repository.DeliveryRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.ordering.service.OrderService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Etapa 10 (Fase 1.5) - modulo delivery, ciclo de vida da entrega apos criada
 * (a criacao em si acontece dentro de OrderService.create, ver nota la).
 *
 * Maquina de estado (Etapa 2), com uma simplificacao deliberada: a transicao
 * "ATRIBUIDA" (dispatcher escolhe o entregador manualmente) foi substituida
 * por auto-atribuicao competitiva - qualquer entregador DISPONIVEL pode
 * chamar "accept" e o primeiro que conseguir salvar (protegido por
 * @Version) fica com a entrega:
 *
 *   AGUARDANDO_ENTREGADOR -> ACEITA -> RETIRADA_NO_RESTAURANTE -> EM_ROTA
 *   -> ENTREGUE (com codigo de confirmacao)
 *   ou EM_ROTA -> NAO_ENTREGUE (gera DeliveryAttempt) -> DEVOLVIDA
 *   qualquer estado antes de EM_ROTA -> CANCELADA
 *
 * CONCORRENCIA (requisito central do prompt original - "dois entregadores
 * tentam aceitar a mesma entrega -> apenas um deve conseguir"): o metodo
 * accept() le a entrega, valida o status em memoria, e tenta salvar. Se dois
 * entregadores chamarem accept() ao mesmo tempo para a mesma entrega, ambos
 * leem status=AGUARDANDO_ENTREGADOR e version=N; o primeiro save() bem
 * sucedido avanca a version para N+1; o segundo save() falha com
 * ObjectOptimisticLockingFailureException (JPA compara a version antes de
 * commitar), que o GlobalExceptionHandler converte em HTTP 409. O segundo
 * entregador recebe um erro claro e pode tentar outra entrega disponivel.
 */
@Service
public class DeliveryService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "AGUARDANDO_ENTREGADOR", Set.of("ACEITA", "CANCELADA"),
            "ACEITA", Set.of("RETIRADA_NO_RESTAURANTE", "CANCELADA"),
            "RETIRADA_NO_RESTAURANTE", Set.of("EM_ROTA", "CANCELADA"),
            "EM_ROTA", Set.of("ENTREGUE", "NAO_ENTREGUE"),
            "NAO_ENTREGUE", Set.of("DEVOLVIDA", "EM_ROTA"),
            "ENTREGUE", Set.of(),
            "DEVOLVIDA", Set.of(),
            "CANCELADA", Set.of()
    );

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final CourierRepository courierRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderService orderService;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            CourierRepository courierRepository,
            CurrentUserProvider currentUserProvider,
            OrderService orderService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.courierRepository = courierRepository;
        this.currentUserProvider = currentUserProvider;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public DeliveryResponse findByOrderId(Long orderId) {
        return toResponse(getByOrderIdOrThrow(orderId));
    }

    /**
     * Auto-atribuicao competitiva. O entregador autenticado precisa ter um
     * cadastro em "couriers" vinculado ao seu usuario.
     */
    @Transactional
    public DeliveryResponse accept(Long deliveryId) {
        Courier courier = courierRepository.findByUserId(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("Usuario atual nao e um entregador cadastrado"));

        Delivery delivery = getOrThrow(deliveryId);
        validateTransition(delivery.getStatus(), "ACEITA");

        delivery.setCourierId(courier.getId());
        delivery.setStatus("ACEITA");
        delivery.setAcceptedAt(Instant.now());

        try {
            Delivery saved = deliveryRepository.save(delivery);
            courier.setStatus("OCUPADO");
            courierRepository.save(courier);
            return toResponse(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConcurrencyConflictException(
                    "Esta entrega ja foi aceita por outro entregador");
        }
    }

    @Transactional
    public DeliveryResponse pickup(Long deliveryId) {
        Delivery delivery = getOrThrow(deliveryId);
        validateTransition(delivery.getStatus(), "RETIRADA_NO_RESTAURANTE");
        delivery.setStatus("RETIRADA_NO_RESTAURANTE");
        delivery.setPickedUpAt(Instant.now());
        return toResponse(deliveryRepository.save(delivery));
    }

    @Transactional
    public DeliveryResponse leaveForDelivery(Long deliveryId) {
        Delivery delivery = getOrThrow(deliveryId);
        validateTransition(delivery.getStatus(), "EM_ROTA");
        delivery.setStatus("EM_ROTA");
        delivery.setLeftAt(Instant.now());
        Delivery saved = deliveryRepository.save(delivery);

        orderService.transitionTo(delivery.getOrderId(), "SAIU_PARA_ENTREGA", null);

        return toResponse(saved);
    }

    @Transactional
    public DeliveryResponse confirmDelivery(Long deliveryId, ConfirmDeliveryRequest request) {
        Delivery delivery = getOrThrow(deliveryId);
        validateTransition(delivery.getStatus(), "ENTREGUE");

        if (!delivery.getConfirmationCode().equals(request.confirmationCode())) {
            throw new BusinessException("Codigo de confirmacao invalido");
        }

        delivery.setStatus("ENTREGUE");
        delivery.setReceivedByName(request.receivedByName());
        delivery.setDeliveredAt(Instant.now());
        Delivery saved = deliveryRepository.save(delivery);

        freeCourier(delivery.getCourierId());
        orderService.transitionTo(delivery.getOrderId(), "ENTREGUE", null);
        orderService.transitionTo(delivery.getOrderId(), "FINALIZADO", null);

        return toResponse(saved);
    }

    @Transactional
    public DeliveryResponse reportFailure(Long deliveryId, DeliveryFailureRequest request) {
        Delivery delivery = getOrThrow(deliveryId);
        validateTransition(delivery.getStatus(), "NAO_ENTREGUE");
        delivery.setStatus("NAO_ENTREGUE");
        Delivery saved = deliveryRepository.save(delivery);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setDeliveryId(delivery.getId());
        attempt.setAttemptedAt(Instant.now());
        attempt.setOutcome("NAO_ENTREGUE");
        attempt.setReason(request.reason());
        attempt.setNextAction(request.nextAction());
        deliveryAttemptRepository.save(attempt);

        return toResponse(saved);
    }

    private void freeCourier(Long courierId) {
        if (courierId == null) return;
        courierRepository.findById(courierId).ifPresent(courier -> {
            courier.setStatus("DISPONIVEL");
            courierRepository.save(courier);
        });
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para entrega: " + from + " -> " + to);
        }
    }

    private Delivery getOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", id));
    }

    private Delivery getByOrderIdOrThrow(Long orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery para o pedido", orderId));
    }

    private DeliveryResponse toResponse(Delivery d) {
        return new DeliveryResponse(
                d.getId(), d.getOrderId(), d.getCourierId(), d.getAddressSnapshot(), d.getNeighborhoodSnapshot(),
                d.getFee(), d.getEstimatedMinutes(), d.getStatus(), d.getReceivedByName(),
                d.getAssignedAt(), d.getAcceptedAt(), d.getPickedUpAt(), d.getLeftAt(), d.getDeliveredAt()
        );
    }
}
