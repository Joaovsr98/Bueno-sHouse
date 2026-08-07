package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.inventory.domain.*;
import com.restaurante.sistema.modules.inventory.dto.*;
import com.restaurante.sistema.modules.inventory.repository.InventoryItemRepository;
import com.restaurante.sistema.modules.inventory.repository.RecipeRepository;
import com.restaurante.sistema.modules.inventory.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Etapa 11 (Fase 2) - modulo inventory, agora COM automacao de baixa (a
 * Etapa 3 modelou tudo mas deixou a baixa automatica deliberadamente fora do
 * MVP - ver decisao da Etapa 1).
 *
 * REGRA DE BAIXA (decisao registrada desde a Etapa 2, agora implementada):
 * o estoque e baixado no momento em que o OrderItem e marcado como PRONTO
 * (producao real concluida), nao na criacao do pedido - evita baixar
 * ingrediente de item cancelado antes do preparo. Quem chama
 * deductForOrderItem() e o KitchenService, no metodo complete().
 *
 * Protecao contra baixa duplicada: cada baixa gera um StockMovement do tipo
 * SAIDA_PRODUCAO referenciando o order_item_id; antes de baixar, verificamos
 * se ja existe um movimento desse tipo para o mesmo item (idempotencia -
 * chamar complete() duas vezes no mesmo item, se algum dia isso for
 * possivel, nao duplica a baixa).
 */
@Service
public class InventoryService {

    private static final String STOCK_OUT_TYPE = "SAIDA_PRODUCAO";
    private static final Set<String> MANUAL_MOVEMENT_TYPES = Set.of("ENTRADA_COMPRA", "AJUSTE_PERDA");

    private final InventoryItemRepository inventoryItemRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CurrentUserProvider currentUserProvider;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            RecipeRepository recipeRepository,
            StockMovementRepository stockMovementRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.recipeRepository = recipeRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listByUnit(Long unitId) {
        return inventoryItemRepository.findByUnitId(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listBelowMinimum(Long unitId) {
        return inventoryItemRepository.findBelowMinimum(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public InventoryItemResponse create(InventoryItemRequest request) {
        InventoryItem item = new InventoryItem();
        item.setUnitId(request.unitId());
        item.setName(request.name());
        item.setUnitOfMeasure(request.unitOfMeasure());
        item.setMinimumQuantity(request.minimumQuantity() != null ? request.minimumQuantity() : BigDecimal.ZERO);
        item.setCostPerUnit(request.costPerUnit() != null ? request.costPerUnit() : BigDecimal.ZERO);
        return toResponse(inventoryItemRepository.save(item));
    }

    @Transactional
    public RecipeResponse saveRecipe(RecipeRequest request) {
        Recipe recipe = recipeRepository.findByProductId(request.productId()).orElseGet(() -> {
            Recipe r = new Recipe();
            r.setProductId(request.productId());
            return r;
        });

        recipe.getItems().clear();
        for (RecipeItemRequest itemRequest : request.items()) {
            InventoryItem inventoryItem = getOrThrow(itemRequest.inventoryItemId());
            RecipeItem recipeItem = new RecipeItem();
            recipeItem.setRecipe(recipe);
            recipeItem.setInventoryItemId(inventoryItem.getId());
            recipeItem.setQuantity(itemRequest.quantity());
            recipe.getItems().add(recipeItem);
        }

        Recipe saved = recipeRepository.save(recipe);
        return toRecipeResponse(saved);
    }

    @Transactional
    public StockMovementResponse registerManualMovement(Long inventoryItemId, StockMovementRequest request) {
        if (!MANUAL_MOVEMENT_TYPES.contains(request.type())) {
            throw new BusinessException(
                    "Tipo de movimento manual invalido: " + request.type()
                            + ". Baixa por producao (SAIDA_PRODUCAO) e sempre automatica.");
        }

        InventoryItem item = getOrThrow(inventoryItemId);

        BigDecimal delta = "AJUSTE_PERDA".equals(request.type()) ? request.quantity().negate() : request.quantity();
        item.setCurrentQuantity(item.getCurrentQuantity().add(delta));
        inventoryItemRepository.save(item);

        StockMovement movement = new StockMovement();
        movement.setInventoryItemId(item.getId());
        movement.setType(request.type());
        movement.setQuantity(request.quantity());
        movement.setReason(request.reason());
        movement.setRegisteredBy(currentUserProvider.getCurrentUserId());
        movement.setCreatedAt(Instant.now());
        StockMovement saved = stockMovementRepository.save(movement);

        return new StockMovementResponse(saved.getId(), saved.getType(), saved.getQuantity(), saved.getReason(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> listMovements(Long inventoryItemId) {
        return stockMovementRepository.findByInventoryItemIdOrderByCreatedAtDesc(inventoryItemId).stream()
                .map(m -> new StockMovementResponse(m.getId(), m.getType(), m.getQuantity(), m.getReason(), m.getCreatedAt()))
                .toList();
    }

    /**
     * Chamado pelo KitchenService quando um OrderItem e marcado PRONTO.
     * Se o produto nao tiver ficha tecnica cadastrada, nao faz nada
     * silenciosamente (nem todo produto precisa controlar estoque - regra
     * pragmatica: so baixa o que foi explicitamente modelado).
     */
    @Transactional
    public void deductForOrderItem(Long productId, Long orderItemId, int quantitySold) {
        if (stockMovementRepository.existsByReferenceOrderItemIdAndType(orderItemId, STOCK_OUT_TYPE)) {
            return; // ja baixado - idempotencia
        }

        recipeRepository.findByProductId(productId).ifPresent(recipe -> {
            for (RecipeItem recipeItem : recipe.getItems()) {
                InventoryItem inventoryItem = getOrThrow(recipeItem.getInventoryItemId());

                BigDecimal quantityToDeduct = recipeItem.getQuantity().multiply(BigDecimal.valueOf(quantitySold));
                inventoryItem.setCurrentQuantity(inventoryItem.getCurrentQuantity().subtract(quantityToDeduct));
                inventoryItemRepository.save(inventoryItem);

                StockMovement movement = new StockMovement();
                movement.setInventoryItemId(inventoryItem.getId());
                movement.setType(STOCK_OUT_TYPE);
                movement.setQuantity(quantityToDeduct);
                movement.setReferenceOrderItemId(orderItemId);
                movement.setReason("Baixa automatica por producao do item de pedido " + orderItemId);
                movement.setRegisteredBy(currentUserProvider.getCurrentUserId());
                movement.setCreatedAt(Instant.now());
                stockMovementRepository.save(movement);
            }
        });
    }

    private InventoryItem getOrThrow(Long id) {
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id));
    }

    private InventoryItemResponse toResponse(InventoryItem i) {
        return new InventoryItemResponse(
                i.getId(), i.getUnitId(), i.getName(), i.getUnitOfMeasure(), i.getCurrentQuantity(),
                i.getMinimumQuantity(), i.getCostPerUnit(), i.getCurrentQuantity().compareTo(i.getMinimumQuantity()) < 0
        );
    }

    private RecipeResponse toRecipeResponse(Recipe r) {
        List<RecipeResponse.Item> items = r.getItems().stream().map(ri -> {
            InventoryItem inventoryItem = inventoryItemRepository.findById(ri.getInventoryItemId()).orElse(null);
            String name = inventoryItem != null ? inventoryItem.getName() : "?";
            String uom = inventoryItem != null ? inventoryItem.getUnitOfMeasure() : "?";
            return new RecipeResponse.Item(ri.getInventoryItemId(), name, ri.getQuantity(), uom);
        }).toList();

        return new RecipeResponse(r.getId(), r.getProductId(), items);
    }
}
