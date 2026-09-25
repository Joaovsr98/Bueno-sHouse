package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;

/** Cotacao comparativa de um ingrediente entre fornecedores (RF-023), ordenada por preco. */
public record QuoteResponse(Long supplierId, String supplierName, BigDecimal price, String unitOfMeasure) {}
