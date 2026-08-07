package com.restaurante.sistema.modules.ordering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * channel: SALAO, BALCAO, RETIRADA, DELIVERY.
 *
 * Para canal SALAO, commandId e obrigatorio (a comanda precisa estar ABERTA
 * ou EM_ATENDIMENTO). Para BALCAO/RETIRADA, commandId e ignorado.
 *
 * Para canal DELIVERY, customerId e customerAddressId sao obrigatorios - o
 * endereco e a zona de entrega (buscada pelo bairro do endereco) determinam
 * a taxa de entrega, que e somada ao total do pedido automaticamente.
 */
public record CreateOrderRequest(
        @NotNull Long unitId,
        @NotNull String channel,
        Long commandId,
        Long customerId,
        Long customerAddressId,
        String notes,

        @NotEmpty(message = "O pedido precisa ter pelo menos um item")
        @Valid
        List<CreateOrderItemRequest> items
) {}
