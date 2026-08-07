package com.restaurante.sistema.modules.ordering.service;

import com.restaurante.sistema.common.event.OrderReadyEvent;
import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.catalog.domain.Additional;
import com.restaurante.sistema.modules.catalog.domain.Product;
import com.restaurante.sistema.modules.catalog.domain.ProductVariation;
import com.restaurante.sistema.modules.catalog.repository.AdditionalRepository;
import com.restaurante.sistema.modules.catalog.repository.ProductRepository;
import com.restaurante.sistema.modules.catalog.repository.ProductVariationRepository;
import com.restaurante.sistema.modules.dinein.repository.CommandRepository;
import com.restaurante.sistema.modules.customers.domain.CustomerAddress;
import com.restaurante.sistema.modules.customers.repository.CustomerAddressRepository;
import com.restaurante.sistema.modules.customers.service.CustomerService;
import com.restaurante.sistema.modules.delivery.domain.Delivery;
import com.restaurante.sistema.modules.delivery.domain.DeliveryZone;
import com.restaurante.sistema.modules.delivery.repository.DeliveryRepository;
import com.restaurante.sistema.modules.delivery.repository.DeliveryZoneRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.kitchen.service.KitchenEventPublisher;
import com.restaurante.sistema.modules.ordering.domain.Order;
import com.restaurante.sistema.modules.ordering.domain.OrderItem;
import com.restaurante.sistema.modules.ordering.domain.OrderItemAdditional;
import com.restaurante.sistema.modules.ordering.domain.OrderStatusHistory;
import com.restaurante.sistema.modules.ordering.dto.*;
import com.restaurante.sistema.modules.ordering.repository.OrderItemRepository;
import com.restaurante.sistema.modules.ordering.repository.OrderRepository;
import com.restaurante.sistema.modules.ordering.repository.OrderStatusHistoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 8 - modulo ordering.
 *
 * REGRA CRITICA DO PROJETO (nunca confiar no preco do frontend): este service
 * e o UNICO lugar que define o preco de um item de pedido. O preco vem sempre
 * do catalogo (ProductRepository/ProductVariationRepository/AdditionalRepository),
 * nunca do request. O request so envia productId/variationId/additionalIds -
 * o preco enviado pelo cliente, se algum dia existir no DTO, seria ignorado.
 *
 * Maquina de estado do Order (Etapa 2), com simplificacao para o MVP: criar
 * um pedido ja o coloca em RECEBIDO diretamente (pulando RASCUNHO /
 * AGUARDANDO_CONFIRMACAO / AGUARDANDO_PAGAMENTO, que fazem mais sentido
 * quando houver pagamento online - Etapa 9). O restante da maquina de estado
 * e respeitado integralmente.
 */
@Service
public class OrderService {

    private static final java.util.Map<String, Set<String>> ALLOWED_TRANSITIONS = java.util.Map.ofEntries(
            java.util.Map.entry("RASCUNHO", Set.of("AGUARDANDO_CONFIRMACAO", "CANCELADO")),
            java.util.Map.entry("AGUARDANDO_CONFIRMACAO", Set.of("AGUARDANDO_PAGAMENTO", "RECEBIDO", "CANCELADO")),
            java.util.Map.entry("AGUARDANDO_PAGAMENTO", Set.of("PAGAMENTO_CONFIRMADO", "CANCELADO")),
            java.util.Map.entry("PAGAMENTO_CONFIRMADO", Set.of("RECEBIDO", "CANCELADO")),
            java.util.Map.entry("RECEBIDO", Set.of("ENVIADO_PARA_COZINHA", "CANCELADO")),
            java.util.Map.entry("ENVIADO_PARA_COZINHA", Set.of("EM_PREPARO", "CANCELADO")),
            java.util.Map.entry("EM_PREPARO", Set.of("PARCIALMENTE_PRONTO", "PRONTO")),
            java.util.Map.entry("PARCIALMENTE_PRONTO", Set.of("PRONTO")),
            java.util.Map.entry("PRONTO", Set.of("AGUARDANDO_RETIRADA", "AGUARDANDO_ENTREGADOR", "FINALIZADO")),
            java.util.Map.entry("AGUARDANDO_RETIRADA", Set.of("FINALIZADO")),
            java.util.Map.entry("AGUARDANDO_ENTREGADOR", Set.of("SAIU_PARA_ENTREGA")),
            java.util.Map.entry("SAIU_PARA_ENTREGA", Set.of("ENTREGUE")),
            java.util.Map.entry("ENTREGUE", Set.of("FINALIZADO")),
            java.util.Map.entry("FINALIZADO", Set.of()),
            java.util.Map.entry("CANCELADO", Set.of())
    );

    private static final Set<String> NOT_CANCELLABLE = Set.of("EM_PREPARO", "PARCIALMENTE_PRONTO", "PRONTO",
            "AGUARDANDO_RETIRADA", "AGUARDANDO_ENTREGADOR", "SAIU_PARA_ENTREGA", "ENTREGUE", "FINALIZADO", "CANCELADO");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariationRepository variationRepository;
    private final AdditionalRepository additionalRepository;
    private final CommandRepository commandRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final DeliveryRepository deliveryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final KitchenEventPublisher kitchenEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CustomerService customerService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            ProductRepository productRepository,
            ProductVariationRepository variationRepository,
            AdditionalRepository additionalRepository,
            CommandRepository commandRepository,
            CustomerAddressRepository customerAddressRepository,
            DeliveryZoneRepository deliveryZoneRepository,
            DeliveryRepository deliveryRepository,
            CurrentUserProvider currentUserProvider,
            KitchenEventPublisher kitchenEventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            CustomerService customerService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.productRepository = productRepository;
        this.variationRepository = variationRepository;
        this.additionalRepository = additionalRepository;
        this.commandRepository = commandRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.deliveryZoneRepository = deliveryZoneRepository;
        this.deliveryRepository = deliveryRepository;
        this.currentUserProvider = currentUserProvider;
        this.kitchenEventPublisher = kitchenEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByUnit(Long unitId, List<String> statuses) {
        List<String> filter = (statuses == null || statuses.isEmpty())
                ? List.of("RECEBIDO", "ENVIADO_PARA_COZINHA", "EM_PREPARO", "PARCIALMENTE_PRONTO", "PRONTO")
                : statuses;
        return orderRepository.findByUnitIdAndStatusIn(unitId, filter).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    /** Pedidos do cliente logado (app do cliente), mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Consulta de pedido com checagem de propriedade: o cliente so ve o proprio
     * pedido. Retorna 404 (nao 403) se nao for dono, para nao revelar a
     * existencia do pedido de outro cliente.
     */
    @Transactional(readOnly = true)
    public OrderResponse findByIdForCustomer(Long id, Long customerId) {
        Order order = getOrThrow(id);
        if (!customerId.equals(order.getCustomerId())) {
            throw new ResourceNotFoundException("Order", id);
        }
        return toResponse(order);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest incoming) {
        final CreateOrderRequest request = applyClienteRulesIfNeeded(incoming);
        validateChannel(request.channel());

        if ("SALAO".equals(request.channel())) {
            if (request.commandId() == null) {
                throw new BusinessException("Pedidos do canal SALAO exigem uma comanda (commandId)");
            }
            var command = commandRepository.findById(request.commandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Command", request.commandId()));
            if (!Set.of("ABERTA", "EM_ATENDIMENTO").contains(command.getStatus())) {
                throw new BusinessException(
                        "A comanda precisa estar ABERTA ou EM_ATENDIMENTO para receber pedidos (status atual: "
                                + command.getStatus() + ")");
            }
        }

        CustomerAddress deliveryAddress = null;
        DeliveryZone deliveryZone = null;

        if ("DELIVERY".equals(request.channel())) {
            if (request.customerId() == null || request.customerAddressId() == null) {
                throw new BusinessException("Pedidos DELIVERY exigem customerId e customerAddressId");
            }
            deliveryAddress = customerAddressRepository.findById(request.customerAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", request.customerAddressId()));

            if (!deliveryAddress.getCustomer().getId().equals(request.customerId())) {
                throw new BusinessException("O endereco informado nao pertence ao cliente informado");
            }

            String neighborhood = deliveryAddress.getNeighborhood();
            deliveryZone = deliveryZoneRepository
                    .findByUnitIdAndNeighborhoodAndActiveTrue(request.unitId(), neighborhood)
                    .orElseThrow(() -> new BusinessException(
                            "Nao ha area de entrega configurada para o bairro: " + neighborhood));
        }

        Order order = new Order();
        order.setUnitId(request.unitId());
        order.setChannel(request.channel());
        order.setCommandId(request.commandId());
        order.setCustomerId(request.customerId());
        order.setCreatedBy(currentUserProvider.getCurrentUserId());
        order.setNotes(request.notes());
        order.setOrderNumber(nextOrderNumber(request.unitId()));
        order.setStatus("RECEBIDO");
        order.setCreatedAt(Instant.now());

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.items()) {
            OrderItem item = buildOrderItem(order, itemRequest);
            order.getItems().add(item);
            subtotal = subtotal.add(item.getSubtotal());
        }

        order.setSubtotal(subtotal);

        if (deliveryZone != null) {
            if (subtotal.compareTo(deliveryZone.getMinimumOrderValue()) < 0) {
                throw new BusinessException(
                        "Pedido minimo para esta area de entrega e " + deliveryZone.getMinimumOrderValue());
            }
            order.setDeliveryFee(deliveryZone.getFee());
        }

        order.setTotal(subtotal.add(order.getServiceFee()).add(order.getDeliveryFee()).subtract(order.getDiscount()));

        Order saved = saveWithRetryOnOrderNumberConflict(order, request.unitId());
        recordHistory(saved.getId(), null, "RECEBIDO", "Pedido criado");

        if (deliveryAddress != null) {
            createDeliveryForOrder(saved, deliveryAddress, deliveryZone);
        }

        return toResponse(saved);
    }

    private void createDeliveryForOrder(Order order, CustomerAddress address, DeliveryZone zone) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(order.getId());
        delivery.setDeliveryZoneId(zone.getId());

        String fullAddress = String.format("%s, %s%s - %s, %s/%s - CEP %s",
                address.getStreet(), address.getNumber(),
                address.getComplement() != null ? " (" + address.getComplement() + ")" : "",
                address.getNeighborhood(), address.getCity(), address.getState(), address.getZipCode());

        delivery.setAddressSnapshot(fullAddress);
        delivery.setNeighborhoodSnapshot(address.getNeighborhood());
        delivery.setReferencePointSnapshot(address.getReferencePoint());
        delivery.setFee(zone.getFee());
        delivery.setEstimatedMinutes(zone.getEstimatedMinutes());
        delivery.setStatus("AGUARDANDO_ENTREGADOR");
        delivery.setConfirmationCode(generateConfirmationCode());

        deliveryRepository.save(delivery);
    }

    private String generateConfirmationCode() {
        int code = 100000 + (int) (Math.random() * 900000);
        return String.valueOf(code);
    }

    @Transactional
    public OrderResponse sendToKitchen(Long orderId) {
        Order order = getOrThrow(orderId);
        validateTransition(order.getStatus(), "ENVIADO_PARA_COZINHA");

        order.getItems().forEach(item -> {
            if ("PENDENTE".equals(item.getStatus())) {
                item.setStatus("ENVIADO");
                kitchenEventPublisher.publishItemEvent(
                        order.getUnitId(), "ITEM_SENT", item.getId(), order.getId(), "ENVIADO");
            }
        });

        String previous = order.getStatus();
        order.setStatus("ENVIADO_PARA_COZINHA");
        Order saved = orderRepository.save(order);
        recordHistory(order.getId(), previous, "ENVIADO_PARA_COZINHA", null);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse transitionTo(Long orderId, String newStatus, String reason) {
        Order order = getOrThrow(orderId);
        validateTransition(order.getStatus(), newStatus);

        String previous = order.getStatus();
        order.setStatus(newStatus);

        if ("FINALIZADO".equals(newStatus)) {
            order.setCompletedAt(Instant.now());
        }
        if ("CANCELADO".equals(newStatus)) {
            order.setCancelReason(reason);
        }

        Order saved = orderRepository.save(order);
        recordHistory(order.getId(), previous, newStatus, reason);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse cancel(Long orderId, String reason) {
        Order order = getOrThrow(orderId);

        if (NOT_CANCELLABLE.contains(order.getStatus())) {
            throw new BusinessException(
                    "Pedido nao pode mais ser cancelado diretamente (status atual: " + order.getStatus()
                            + "). Ja entrou em producao ou finalizou - requer fluxo de cancelamento com autorizacao.");
        }

        return transitionTo(orderId, "CANCELADO", reason);
    }

    @Transactional
    public void recomputeStatusFromItems(Long orderId) {
        Order order = getOrThrow(orderId);
        List<OrderItem> items = order.getItems();

        boolean anyInProgress = items.stream().anyMatch(i -> "EM_PREPARO".equals(i.getStatus()));
        boolean allDone = items.stream().allMatch(i -> Set.of("PRONTO", "CANCELADO", "ENTREGUE").contains(i.getStatus()));
        boolean anyReady = items.stream().anyMatch(i -> "PRONTO".equals(i.getStatus()));

        String target = null;
        if (allDone && Set.of("ENVIADO_PARA_COZINHA", "EM_PREPARO", "PARCIALMENTE_PRONTO").contains(order.getStatus())) {
            target = "PRONTO";
        } else if (anyReady && "EM_PREPARO".equals(order.getStatus())) {
            target = "PARCIALMENTE_PRONTO";
        } else if (anyInProgress && "ENVIADO_PARA_COZINHA".equals(order.getStatus())) {
            target = "EM_PREPARO";
        }

        if (target != null && ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).contains(target)) {
            String previous = order.getStatus();
            order.setStatus(target);
            orderRepository.save(order);
            recordHistory(orderId, previous, target, "Recalculado automaticamente a partir dos itens");

            if ("PRONTO".equals(target)) {
                // OrderService nao conhece NotificationService diretamente (Etapa 14/Fase 2 -
                // desacoplamento via evento de dominio, ver OrderNotificationListener).
                applicationEventPublisher.publishEvent(
                        new OrderReadyEvent(order.getId(), order.getOrderNumber(), order.getCreatedBy()));
            }

            // Pedido DELIVERY pronto ja entra automaticamente na fila de busca de entregador
            // (a Delivery em si ja existe desde a criacao do pedido, com status AGUARDANDO_ENTREGADOR)
            if ("PRONTO".equals(target) && "DELIVERY".equals(order.getChannel())) {
                order.setStatus("AGUARDANDO_ENTREGADOR");
                orderRepository.save(order);
                recordHistory(orderId, "PRONTO", "AGUARDANDO_ENTREGADOR", "Pedido delivery liberado para retirada");
            }
        }
    }

    /**
     * Se quem cria o pedido e um usuario CLIENTE (app do cliente), forcamos:
     * canal DELIVERY, sem comanda, e o customerId do proprio cliente logado -
     * ignorando o que veio no request, para um cliente nunca pedir "como
     * outro". O endereco enviado ainda e validado (precisa pertencer a esse
     * cliente) pela regra de DELIVERY ja existente no create().
     */
    private CreateOrderRequest applyClienteRulesIfNeeded(CreateOrderRequest request) {
        String profile = currentUserProvider.getCurrentUser().getProfile().getName();
        if (!"CLIENTE".equals(profile)) {
            return request;
        }
        Long myCustomerId = customerService.customerIdOfUser(currentUserProvider.getCurrentUserId());
        return new CreateOrderRequest(
                request.unitId(),
                "DELIVERY",
                null,
                myCustomerId,
                request.customerAddressId(),
                request.notes(),
                request.items()
        );
    }

    private void validateChannel(String channel) {
        if (!Set.of("SALAO", "BALCAO", "RETIRADA", "DELIVERY").contains(channel)) {
            throw new BusinessException("Canal invalido: " + channel);
        }
    }

    private OrderItem buildOrderItem(Order order, CreateOrderItemRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));

        if (product.getDeletedAt() != null || !product.isAvailable()) {
            throw new BusinessException("Produto indisponivel: " + product.getName());
        }

        BigDecimal unitPrice = product.getBasePrice();

        if (request.variationId() != null) {
            ProductVariation variation = variationRepository.findById(request.variationId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariation", request.variationId()));
            unitPrice = unitPrice.add(variation.getPriceDelta());
        }

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(product.getId());
        item.setProductNameSnapshot(product.getName());
        item.setUnitPriceSnapshot(unitPrice);
        item.setQuantity(request.quantity());
        item.setNotes(request.notes());
        item.setVariationId(request.variationId());
        item.setStatus("PENDENTE");
        item.setKitchenSectorId(product.getKitchenSector() != null ? product.getKitchenSector().getId() : null);

        BigDecimal additionalsTotal = BigDecimal.ZERO;
        if (request.additionalIds() != null) {
            for (Long additionalId : request.additionalIds()) {
                Additional additional = additionalRepository.findById(additionalId)
                        .orElseThrow(() -> new ResourceNotFoundException("Additional", additionalId));

                OrderItemAdditional oia = new OrderItemAdditional();
                oia.setOrderItem(item);
                oia.setAdditionalId(additional.getId());
                oia.setAdditionalNameSnapshot(additional.getName());
                oia.setPriceSnapshot(additional.getPrice());
                oia.setQuantity(1);
                item.getAdditionals().add(oia);

                additionalsTotal = additionalsTotal.add(additional.getPrice());
            }
        }

        BigDecimal itemSubtotal = unitPrice.add(additionalsTotal).multiply(BigDecimal.valueOf(request.quantity()));
        item.setSubtotal(itemSubtotal);

        return item;
    }

    private Long nextOrderNumber(Long unitId) {
        return orderRepository.findMaxOrderNumberForUnit(unitId) + 1;
    }

    private Order saveWithRetryOnOrderNumberConflict(Order order, Long unitId) {
        try {
            return orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            order.setOrderNumber(nextOrderNumber(unitId));
            return orderRepository.save(order);
        }
    }

    private void recordHistory(Long orderId, String from, String to, String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(currentUserProvider.getCurrentUserId());
        history.setChangedAt(Instant.now());
        history.setReason(reason);
        statusHistoryRepository.save(history);
    }

    private void validateTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Transicao de status invalida para pedido: " + from + " -> " + to);
        }
    }

    Order getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream().map(this::toItemResponse).toList();

        return new OrderResponse(
                o.getId(), o.getPublicId(), o.getOrderNumber(), o.getUnitId(), o.getChannel(), o.getCommandId(),
                o.getStatus(), o.getSubtotal(), o.getDiscount(), o.getServiceFee(), o.getDeliveryFee(), o.getTotal(),
                o.getNotes(), o.getCreatedAt(), o.getCompletedAt(), items
        );
    }

    private OrderItemResponse toItemResponse(OrderItem i) {
        List<OrderItemAdditionalResponse> additionals = i.getAdditionals().stream()
                .map(a -> new OrderItemAdditionalResponse(a.getId(), a.getAdditionalNameSnapshot(), a.getPriceSnapshot(), a.getQuantity()))
                .toList();

        return new OrderItemResponse(
                i.getId(), i.getProductId(), i.getProductNameSnapshot(), i.getUnitPriceSnapshot(), i.getQuantity(),
                i.getSubtotal(), i.getNotes(), i.getStatus(), i.getKitchenSectorId(), i.getStartedAt(), i.getCompletedAt(),
                additionals
        );
    }
}
