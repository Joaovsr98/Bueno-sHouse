package com.restaurante.sistema.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductVariationRequest(
        Long id, // null = nova variacao; preenchido = atualizar existente

        @NotBlank(message = "O nome da variacao e obrigatorio")
        String name,

        @NotNull(message = "O valor adicional (mesmo que zero) e obrigatorio")
        BigDecimal priceDelta
) {}
