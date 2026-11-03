package com.restaurante.sistema.modules.delivery.service;

import com.restaurante.sistema.common.event.DeliveryProgressEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 10 (Fase 1.5) - modulo delivery, ciclo de vida da entrega apos criada
 * (a criacao em si acontece dentro de OrderService.create, ver nota la). O avanco do pedido
 * junto com a entrega e feito por evento (DeliveryProgressEvent -> OrderDeliveryEventListener).
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
    private final ApplicationEventPublisher eventPublisher;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            CourierRepository courierRepository,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher
    ) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.courierRepository = courierRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
    }

    /** Situacoes em que a entrega ainda esta com o motoboy (aceita, retirada, em rota ou com tentativa falha). */
    private static final List<String> IN_HANDS = List.of("ACEITA", "RETIRADA_NO_RESTAURANTE", "EM_ROTA", "NAO_ENTREGUE");

    /**
     * Fila do motoboy: so entregas de pedidos prontos, da que espera ha mais tempo para a
     * mais nova. A primeira e a unica que pode ser pega por quem esta sem entrega.
     */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> listAvailable() {
        return queue().stream().map(this::toResponse).toList();
    }

    private List<Delivery> queue() {
        return deliveryRepository.findByStatusAndReleasedAtIsNotNullOrderByReleasedAtAscIdAsc("AGUARDANDO_ENTREGADOR");
    }

    /**
     * Entregas em andamento do motoboy logado. Recarregar a pagina nao "perde" a entrega,
     * e ele pode levar mais de uma no mesmo trajeto.
     */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> listMine() {
        return courierRepository.findByUserId(currentUserProvider.getCurrentUserId())
                .map(c -> deliveryRepository.findByCourierIdAndStatusIn(c.getId(), IN_HANDS).stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public DeliveryResponse findByOrderId(Long orderId) {
        return toResponse(getByOrderIdOrThrow(orderId));
    }

    /**
     * Codigo que o cliente informa ao motoboy na entrega. Nunca vai em
     * DeliveryResponse; quem chama precisa garantir que e o dono do pedido.
     */
    @Transactional(readOnly = true)
    public String confirmationCodeForOrder(Long orderId) {
        return getByOrderIdOrThrow(orderId).getConfirmationCode();
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
        if (delivery.getReleasedAt() == null) {
            throw new BusinessException("Este pedido ainda nao esta pronto para entrega");
        }
        // Fila: sem entrega em maos, o motoboy pega a que espera ha mais tempo; com uma em
        // maos, pode levar outras junto (as que estao no caminho).
        boolean emptyHanded = deliveryRepository.findByCourierIdAndStatusIn(courier.getId(), IN_HANDS).isEmpty();
        if (emptyHanded) {
            Delivery oldest = queue().get(0);
            if (!oldest.getId().equals(delivery.getId())) {
                throw new BusinessException("Pegue primeiro a entrega que espera ha mais tempo (#" + oldest.getId()
                        + "). Com ela em maos, voce pode levar junto as que estiverem no caminho.");
            }
        }

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

        eventPublisher.publishEvent(new DeliveryProgressEvent(delivery.getOrderId(), DeliveryProgressEvent.Stage.LEFT));

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
        eventPublisher.publishEvent(new DeliveryProgressEvent(delivery.getOrderId(), DeliveryProgressEvent.Stage.DELIVERED));

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
        // Ainda levando outra entrega do mesmo trajeto: continua ocupado.
        if (!deliveryRepository.findByCourierIdAndStatusIn(courierId, IN_HANDS).isEmpty()) return;
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
                d.getAssignedAt(), d.getAcceptedAt(), d.getPickedUpAt(), d.getLeftAt(), d.getDeliveredAt(), d.getReleasedAt()
        );
    }
}
