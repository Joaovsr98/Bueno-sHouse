package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.modules.inventory.domain.InventoryItem;
import com.restaurante.sistema.modules.inventory.domain.Purchase;
import com.restaurante.sistema.modules.inventory.domain.PurchaseItem;
import com.restaurante.sistema.modules.inventory.domain.Supplier;
import com.restaurante.sistema.modules.inventory.dto.PurchaseItemRequest;
import com.restaurante.sistema.modules.inventory.dto.PurchaseRequest;
import com.restaurante.sistema.modules.inventory.dto.PurchaseResponse;
import com.restaurante.sistema.modules.inventory.repository.InventoryItemRepository;
import com.restaurante.sistema.modules.inventory.repository.PurchaseRepository;
import com.restaurante.sistema.modules.inventory.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de compra: RASCUNHO -> ENVIADO -> RECEBIDO (ou CANCELADO), RF-024.
 * O recebimento gera entrada de estoque e atualiza o custo unitario de cada
 * ingrediente (RN05), via InventoryService.receiveStock (mesmo mecanismo de
 * StockMovement usado pela baixa automatica de producao).
 */
@Service
public class PurchaseService {

    private static final String RASCUNHO = "RASCUNHO";
    private static final String ENVIADO = "ENVIADO";
    private static final String RECEBIDO = "RECEBIDO";
    private static final String CANCELADO = "CANCELADO";

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryService inventoryService;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            SupplierRepository supplierRepository,
            InventoryItemRepository inventoryItemRepository,
            InventoryService inventoryService
    ) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listByUnit(Long unitId) {
        return purchaseRepository.findByUnitIdOrderByIdDesc(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        if (!supplierRepository.existsById(request.supplierId())) {
            throw new ResourceNotFoundException("Supplier", request.supplierId());
        }

        Purchase purchase = new Purchase();
        purchase.setUnitId(request.unitId());
        purchase.setSupplierId(request.supplierId());
        purchase.setStatus(RASCUNHO);

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemRequest itemRequest : request.items()) {
            if (!inventoryItemRepository.existsById(itemRequest.inventoryItemId())) {
                throw new ResourceNotFoundException("InventoryItem", itemRequest.inventoryItemId());
            }
            BigDecimal subtotal = itemRequest.quantity().multiply(itemRequest.unitPrice());
            total = total.add(subtotal);

            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setInventoryItemId(itemRequest.inventoryItemId());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setSubtotal(subtotal);
            purchase.getItems().add(item);
        }
        purchase.setTotal(total);

        return toResponse(purchaseRepository.save(purchase));
    }

    @Transactional
    public PurchaseResponse send(Long id) {
        Purchase purchase = getOrThrow(id);
        requireStatus(purchase, RASCUNHO, "enviar");
        purchase.setStatus(ENVIADO);
        return toResponse(purchaseRepository.save(purchase));
    }

    @Transactional
    public PurchaseResponse cancel(Long id) {
        Purchase purchase = getOrThrow(id);
        if (RECEBIDO.equals(purchase.getStatus())) {
            throw new BusinessException("Pedido de compra ja recebido nao pode ser cancelado");
        }
        purchase.setStatus(CANCELADO);
        return toResponse(purchaseRepository.save(purchase));
    }

    /** RF-025: recebimento -> entrada de estoque + atualizacao de custo (RN05). */
    @Transactional
    public PurchaseResponse receive(Long id) {
        Purchase purchase = getOrThrow(id);
        requireStatus(purchase, ENVIADO, "receber");

        for (PurchaseItem item : purchase.getItems()) {
            String reason = "Recebimento do pedido de compra " + purchase.getId();
            inventoryService.receiveStock(item.getInventoryItemId(), item.getQuantity(), item.getUnitPrice(), reason);
        }

        purchase.setStatus(RECEBIDO);
        purchase.setPurchasedAt(LocalDate.now());
        return toResponse(purchaseRepository.save(purchase));
    }

    private void requireStatus(Purchase purchase, String expected, String action) {
        if (!expected.equals(purchase.getStatus())) {
            throw new BusinessException(
                    "So e possivel " + action + " um pedido de compra em " + expected
                            + " (status atual: " + purchase.getStatus() + ")");
        }
    }

    private Purchase getOrThrow(Long id) {
        return purchaseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
    }

    private PurchaseResponse toResponse(Purchase p) {
        String supplierName = supplierRepository.findById(p.getSupplierId()).map(Supplier::getName).orElse("?");
        List<PurchaseResponse.Item> items = p.getItems().stream()
                .map(i -> new PurchaseResponse.Item(
                        i.getInventoryItemId(),
                        inventoryItemRepository.findById(i.getInventoryItemId()).map(InventoryItem::getName).orElse("?"),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getSubtotal()
                ))
                .toList();

        return new PurchaseResponse(p.getId(), p.getUnitId(), p.getSupplierId(), supplierName, p.getStatus(), p.getTotal(), p.getPurchasedAt(), items);
    }
}
