package com.restaurante.sistema.modules.inventory.service;

import com.restaurante.sistema.common.exception.BusinessException;
import com.restaurante.sistema.common.exception.ResourceNotFoundException;
import com.restaurante.sistema.common.validation.CnpjValidator;
import com.restaurante.sistema.modules.inventory.domain.InventoryItem;
import com.restaurante.sistema.modules.inventory.domain.Supplier;
import com.restaurante.sistema.modules.inventory.domain.SupplierProduct;
import com.restaurante.sistema.modules.inventory.dto.PriceHistoryPoint;
import com.restaurante.sistema.modules.inventory.dto.QuoteResponse;
import com.restaurante.sistema.modules.inventory.dto.SupplierProductRequest;
import com.restaurante.sistema.modules.inventory.dto.SupplierProductResponse;
import com.restaurante.sistema.modules.inventory.dto.SupplierRequest;
import com.restaurante.sistema.modules.inventory.dto.SupplierResponse;
import com.restaurante.sistema.modules.inventory.repository.InventoryItemRepository;
import com.restaurante.sistema.modules.inventory.repository.SupplierProductRepository;
import com.restaurante.sistema.modules.inventory.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Fornecedores, catalogo por fornecedor e cotacao comparativa (RF-021 a 023). */
@Service
public class SupplierService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierProductRepository supplierProductRepository,
            InventoryItemRepository inventoryItemRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listByUnit(Long unitId) {
        return supplierRepository.findByUnitId(unitId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        validateCnpj(request.document());

        Supplier supplier = new Supplier();
        applyRequest(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        validateCnpj(request.document());

        Supplier supplier = getOrThrow(id);
        applyRequest(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }


    /** Ativa ou desativa o fornecedor; desativado nao recebe novas compras. */
    @Transactional
    public SupplierResponse setActive(Long id, boolean active) {
        Supplier supplier = getOrThrow(id);
        supplier.setActive(active);
        return toResponse(supplierRepository.save(supplier));
    }

    /**
     * RF-026: historico de precos do fornecedor, a partir das compras recebidas
     * (o preco efetivamente pago), em ordem cronologica.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<PriceHistoryPoint> priceHistory(Long supplierId) {
        getOrThrow(supplierId);
        List<Object[]> rows = entityManager.createQuery(
                "SELECT pi.inventoryItemId, p.purchasedAt, pi.unitPrice FROM PurchaseItem pi JOIN pi.purchase p " +
                "WHERE p.supplierId = :supplierId AND p.status = 'RECEBIDO' AND p.purchasedAt IS NOT NULL " +
                "ORDER BY p.purchasedAt ASC, pi.id ASC")
                .setParameter("supplierId", supplierId)
                .getResultList();
        Map<Long, String> names = inventoryItemRepository.findAllById(
                rows.stream().map(r -> (Long) r[0]).distinct().toList()
        ).stream().collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));
        return rows.stream()
                .map(r -> new PriceHistoryPoint((Long) r[0], names.getOrDefault((Long) r[0], "?"),
                        (LocalDate) r[1], (BigDecimal) r[2]))
                .toList();
    }
    @Transactional
    public SupplierProductResponse upsertProduct(Long supplierId, SupplierProductRequest request) {
        getOrThrow(supplierId); // valida que o fornecedor existe
        InventoryItem item = inventoryItemRepository.findById(request.inventoryItemId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", request.inventoryItemId()));

        SupplierProduct product = supplierProductRepository
                .findBySupplierIdAndInventoryItemId(supplierId, request.inventoryItemId())
                .orElseGet(() -> {
                    SupplierProduct sp = new SupplierProduct();
                    sp.setSupplierId(supplierId);
                    sp.setInventoryItemId(item.getId());
                    return sp;
                });

        product.setPrice(request.price());
        product.setUnitOfMeasure(request.unitOfMeasure());

        SupplierProduct saved = supplierProductRepository.save(product);
        return new SupplierProductResponse(saved.getId(), saved.getSupplierId(), saved.getInventoryItemId(), item.getName(), saved.getPrice(), saved.getUnitOfMeasure());
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResponse> listCatalog(Long supplierId) {
        return supplierProductRepository.findBySupplierId(supplierId).stream().map(this::toProductResponse).toList();
    }

    /** Cotacao comparativa entre fornecedores para um ingrediente, ordenada por preco (RF-023). */
    @Transactional(readOnly = true)
    public List<QuoteResponse> compareQuotes(Long inventoryItemId) {
        List<SupplierProduct> offers = supplierProductRepository.findByInventoryItemIdOrderByPriceAsc(inventoryItemId);
        Map<Long, Supplier> suppliers = supplierRepository.findAllById(
                offers.stream().map(SupplierProduct::getSupplierId).toList()
        ).stream().collect(Collectors.toMap(Supplier::getId, s -> s));

        return offers.stream()
                .map(o -> new QuoteResponse(
                        o.getSupplierId(),
                        suppliers.containsKey(o.getSupplierId()) ? suppliers.get(o.getSupplierId()).getName() : "?",
                        o.getPrice(),
                        o.getUnitOfMeasure()
                ))
                .toList();
    }

    private void validateCnpj(String document) {
        if (!CnpjValidator.isValid(document)) {
            throw new BusinessException("CNPJ invalido: " + document);
        }
    }

    private void applyRequest(Supplier supplier, SupplierRequest request) {
        supplier.setUnitId(request.unitId());
        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.setDocument(request.document());
    }

    private Supplier getOrThrow(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getUnitId(), s.getName(), s.getPhone(), s.getDocument(), s.isActive());
    }

    private SupplierProductResponse toProductResponse(SupplierProduct sp) {
        String name = inventoryItemRepository.findById(sp.getInventoryItemId()).map(InventoryItem::getName).orElse("?");
        return new SupplierProductResponse(sp.getId(), sp.getSupplierId(), sp.getInventoryItemId(), name, sp.getPrice(), sp.getUnitOfMeasure());
    }
}
