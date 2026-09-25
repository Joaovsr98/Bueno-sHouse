package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.catalog.domain.Product;
import com.restaurante.sistema.modules.catalog.repository.ProductRepository;
import com.restaurante.sistema.modules.identity.security.CurrentUserProvider;
import com.restaurante.sistema.modules.inventory.domain.*;
import com.restaurante.sistema.modules.inventory.dto.*;
import com.restaurante.sistema.modules.inventory.repository.InventoryItemRepository;
import com.restaurante.sistema.modules.inventory.repository.RecipeRepository;
import com.restaurante.sistema.modules.inventory.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final BigDecimal FOOD_COST_GREEN_MAX = BigDecimal.valueOf(30);
    private static final BigDecimal FOOD_COST_YELLOW_MAX = BigDecimal.valueOf(35);

    private final InventoryItemRepository inventoryItemRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            RecipeRepository recipeRepository,
            StockMovementRepository stockMovementRepository,
            ProductRepository productRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.recipeRepository = recipeRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
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

    /** Food cost medio dos produtos da unidade que tem ficha tecnica e preco (dashboard RF-034). */
    @Transactional(readOnly = true)
    public BigDecimal averageFoodCostPercent(Long unitId) {
        List<Product> products = productRepository.findByUnitIdAndDeletedAtIsNull(unitId);
        List<BigDecimal> percents = new java.util.ArrayList<>();
        for (Product product : products) {
            recipeRepository.findByProductId(product.getId()).ifPresent(recipe -> {
                BigDecimal percent = toRecipeResponse(recipe).foodCostPercent();
                if (percent != null) {
                    percents.add(percent);
                }
            });
        }
        if (percents.isEmpty()) {
            return null;
        }
        BigDecimal sum = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(percents.size()), 2, RoundingMode.HALF_UP);
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

        recipe.setYieldQuantity(request.yieldQuantity());

        recipe.getItems().clear();
        for (RecipeItemRequest itemRequest : request.items()) {
            InventoryItem inventoryItem = getOrThrow(itemRequest.inventoryItemId());
            RecipeItem recipeItem = new RecipeItem();
            recipeItem.setRecipe(recipe);
            recipeItem.setInventoryItemId(inventoryItem.getId());
            recipeItem.setQuantity(itemRequest.quantity());
            recipeItem.setCorrectionFactor(itemRequest.correctionFactor());
            recipe.getItems().add(recipeItem);
        }

        Recipe saved = recipeRepository.save(recipe);
        return toRecipeResponse(saved);
    }

    /** Ficha tecnica de um produto, com custo e food cost calculados (RF-012, RF-013). */
    @Transactional(readOnly = true)
    public RecipeResponse getRecipeByProduct(Long productId) {
        Recipe recipe = recipeRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Ficha tecnica do produto", productId));
        return toRecipeResponse(recipe);
    }

    /**
     * Recebimento de compra (RF-025): entrada de estoque + atualizacao do custo
     * unitario do ingrediente (RN05). Chamado pelo PurchaseService ao confirmar
     * o recebimento de um pedido de compra (RASCUNHO/ENVIADO -> RECEBIDO).
     */
    @Transactional
    public void receiveStock(Long inventoryItemId, BigDecimal quantity, BigDecimal unitCost, String reason) {
        InventoryItem item = getOrThrow(inventoryItemId);
        item.setCurrentQuantity(item.getCurrentQuantity().add(quantity));
        if (unitCost != null) {
            item.setCostPerUnit(unitCost);
        }
        inventoryItemRepository.save(item);

        StockMovement movement = new StockMovement();
        movement.setInventoryItemId(item.getId());
        movement.setType("ENTRADA_COMPRA");
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movement.setRegisteredBy(currentUserProvider.getCurrentUserId());
        movement.setCreatedAt(Instant.now());
        stockMovementRepository.save(movement);
    }

    @Transactional
    public StockMovementResponse registerManualMovement(Long inventoryItemId, StockMovementRequest request) {
        if (!MANUAL_MOVEMENT_TYPES.contains(request.type())) {
            throw new BusinessException(
                    "Tipo de movimento manual invalido: " + request.type()
                            + ". Baixa por producao (SAIDA_PRODUCAO) e sempre automatica.");
        }
        // RF-030: saida manual (perda) sempre precisa de motivo.
        if ("AJUSTE_PERDA".equals(request.type()) && (request.reason() == null || request.reason().isBlank())) {
            throw new BusinessException("Informe o motivo da perda (AJUSTE_PERDA)");
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

    /**
     * Estorno de estoque no cancelamento de pedido (RF-018): reverte toda baixa
     * SAIDA_PRODUCAO ja feita para o item do pedido, se houver. Idempotente -
     * se o item nunca chegou a PRONTO (nada foi baixado) ou ja foi estornado,
     * nao faz nada.
     */
    @Transactional
    public void reverseDeductionForOrderItem(Long orderItemId, String reason) {
        if (stockMovementRepository.existsByReferenceOrderItemIdAndType(orderItemId, "ESTORNO_CANCELAMENTO")) {
            return; // ja estornado
        }

        List<StockMovement> deductions = stockMovementRepository.findByReferenceOrderItemIdAndType(orderItemId, STOCK_OUT_TYPE);
        for (StockMovement deduction : deductions) {
            InventoryItem item = getOrThrow(deduction.getInventoryItemId());
            item.setCurrentQuantity(item.getCurrentQuantity().add(deduction.getQuantity()));
            inventoryItemRepository.save(item);

            StockMovement reversal = new StockMovement();
            reversal.setInventoryItemId(item.getId());
            reversal.setType("ESTORNO_CANCELAMENTO");
            reversal.setQuantity(deduction.getQuantity());
            reversal.setReferenceOrderItemId(orderItemId);
            reversal.setReason(reason != null ? reason : "Estorno por cancelamento do pedido");
            reversal.setRegisteredBy(currentUserProvider.getCurrentUserId());
            reversal.setCreatedAt(Instant.now());
            stockMovementRepository.save(reversal);
        }
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
        BigDecimal totalCost = BigDecimal.ZERO;

        List<RecipeResponse.Item> items = new java.util.ArrayList<>();
        for (RecipeItem ri : r.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository.findById(ri.getInventoryItemId()).orElse(null);
            String name = inventoryItem != null ? inventoryItem.getName() : "?";
            String uom = inventoryItem != null ? inventoryItem.getUnitOfMeasure() : "?";
            items.add(new RecipeResponse.Item(ri.getInventoryItemId(), name, ri.getQuantity(), uom, ri.getCorrectionFactor()));

            if (inventoryItem != null && inventoryItem.getCostPerUnit() != null) {
                // custo_prato = SUM(quantidade x fator_correcao x custo_unitario) / rendimento (RF-012)
                BigDecimal lineCost = ri.getQuantity().multiply(ri.getCorrectionFactor()).multiply(inventoryItem.getCostPerUnit());
                totalCost = totalCost.add(lineCost);
            }
        }

        BigDecimal yieldQuantity = r.getYieldQuantity() != null && r.getYieldQuantity().signum() > 0
                ? r.getYieldQuantity() : BigDecimal.ONE;
        BigDecimal costPerServing = totalCost.divide(yieldQuantity, 4, RoundingMode.HALF_UP);

        Product product = productRepository.findById(r.getProductId()).orElse(null);
        BigDecimal foodCostPercent = null;
        String foodCostLevel = null;
        if (product != null && product.getBasePrice() != null && product.getBasePrice().signum() > 0) {
            // food_cost% = custo_prato / preco_venda x 100 (RF-013)
            foodCostPercent = costPerServing
                    .divide(product.getBasePrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            foodCostLevel = foodCostLevel(foodCostPercent);
        }

        return new RecipeResponse(r.getId(), r.getProductId(), yieldQuantity, items, costPerServing, foodCostPercent, foodCostLevel);
    }

    /** RN02: verde <=30%, amarelo 31-35%, vermelho >35% - so avisa, nunca bloqueia. */
    private String foodCostLevel(BigDecimal foodCostPercent) {
        if (foodCostPercent.compareTo(FOOD_COST_GREEN_MAX) <= 0) {
            return "GREEN";
        }
        if (foodCostPercent.compareTo(FOOD_COST_YELLOW_MAX) <= 0) {
            return "YELLOW";
        }
        return "RED";
    }
}
