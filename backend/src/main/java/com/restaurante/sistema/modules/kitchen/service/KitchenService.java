package com.restaurante.sistema.modules.kitchen.service;

import com.restaurante.sistema.common.event.OrderItemReadyEvent;
import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.kitchen.dto.KitchenTaskResponse;
import com.restaurante.sistema.modules.ordering.domain.OrderItem;
import com.restaurante.sistema.modules.ordering.repository.OrderItemRepository;
import com.restaurante.sistema.modules.ordering.service.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 8 - painel da cozinha.
 *
 * Maquina de estado de OrderItem (Etapa 2):
 *   PENDENTE -> ENVIADO -> EM_PREPARO -> PRONTO -> ENTREGUE
 *   qualquer estado -> CANCELADO (com motivo, ex.: indisponibilidade)
 *
 * Cada mudanca de status aqui: (1) valida a transicao, (2) publica um evento
 * no WebSocket para o painel em tempo real, (3) pede ao OrderService para
 * recalcular o status agregado do pedido (um pedido so fica PRONTO quando
 * todos os itens obrigatorios estiverem PRONTO/CANCELADO - regra central do
 * dominio, ja documentada desde a Etapa 2).
 */
@Service
public class KitchenService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "PENDENTE", Set.of("ENVIADO", "CANCELADO"),
            "ENVIADO", Set.of("EM_PREPARO", "CANCELADO"),
            "EM_PREPARO", Set.of("PRONTO", "CANCELADO"),
            "PRONTO", Set.of("ENTREGUE", "CANCELADO"),
            "ENTREGUE", Set.of(),
            "CANCELADO", Set.of()
    );

    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;
    private final KitchenEventPublisher kitchenEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public KitchenService(
            OrderItemRepository orderItemRepository,
            OrderService orderService,
            CurrentUserProvider currentUserProvider,
            KitchenEventPublisher kitchenEventPublisher,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.currentUserProvider = currentUserProvider;
        this.kitchenEventPublisher = kitchenEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<KitchenTaskResponse> listTasks(Long sectorId, List<String> statuses) {
        List<String> filter = (statuses == null || statuses.isEmpty())
                ? List.of("ENVIADO", "EM_PREPARO")
                : statuses;

        return orderItemRepository.findByKitchenSectorIdAndStatusIn(sectorId, filter).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public KitchenTaskResponse start(Long orderItemId) {
        OrderItem item = getOrThrow(orderItemId);
        validateTransition(item.getStatus(), "EM_PREPARO");

        item.setStatus("EM_PREPARO");
        item.setStartedAt(Instant.now());
        item.setProducedBy(currentUserProvider.getCurrentUserId());
        OrderItem saved = orderItemRepository.save(item);

        kitchenEventPublisher.publishItemEvent(
                unitIdOf(item), "ITEM_STARTED", item.getId(), item.getOrder().getId(), "EM_PREPARO");
        orderService.recomputeStatusFromItems(item.getOrder().getId());

        return toResponse(saved);
    }

    @Transactional
    public KitchenTaskResponse complete(Long orderItemId) {
        OrderItem item = getOrThrow(orderItemId);
        validateTransition(item.getStatus(), "PRONTO");

        item.setStatus("PRONTO");
        item.setCompletedAt(Instant.now());
        OrderItem saved = orderItemRepository.save(item);

        // Baixa automatica de estoque, conforme regra definida na Etapa 2/3:
        // baixar no momento da producao real concluida, nao na criacao do pedido.
        // KitchenService nao conhece InventoryService diretamente (Etapa 14/Fase 2 -
        // desacoplamento via evento de dominio, ver InventoryEventListener).
        applicationEventPublisher.publishEvent(
                new OrderItemReadyEvent(item.getProductId(), item.getId(), item.getQuantity()));

        kitchenEventPublisher.publishItemEvent(
                unitIdOf(item), "ITEM_READY", item.getId(), item.getOrder().getId(), "PRONTO");
        orderService.recomputeStatusFromItems(item.getOrder().getId());

        return toResponse(saved);
    }

    @Transactional
    public KitchenTaskResponse markUnavailable(Long orderItemId, String reason) {
        OrderItem item = getOrThrow(orderItemId);
        validateTransition(item.getStatus(), "CANCELADO");

        item.setStatus("CANCELADO");
        item.setCancelReason(reason);
        OrderItem saved = orderItemRepository.save(item);

        kitchenEventPublisher.publishItemEvent(
                unitIdOf(item), "ITEM_CANCELLED", item.getId(), item.getOrder().getId(), "CANCELADO");
        orderService.recomputeStatusFromItems(item.getOrder().getId());

        return toResponse(saved);
    }

    private Long unitIdOf(OrderItem item) {
        return item.getOrder().getUnitId();
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para item de cozinha: " + from + " -> " + to);
        }
    }

    private OrderItem getOrThrow(Long id) {
        return orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", id));
    }

    private KitchenTaskResponse toResponse(OrderItem i) {
        return new KitchenTaskResponse(
                i.getId(), i.getOrder().getId(), i.getOrder().getOrderNumber(), i.getProductNameSnapshot(),
                i.getQuantity(), i.getNotes(), i.getStatus(), i.getStartedAt(), i.getCompletedAt(),
                i.getOrder().getCreatedAt()
        );
    }
}
