package com.restaurante.sistema.modules.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DeliveryResponse(
        Long id,
        Long orderId,
        Long courierId,
        String addressSnapshot,
        String neighborhoodSnapshot,
        BigDecimal fee,
        Integer estimatedMinutes,
        String status,
        String receivedByName,
        Instant assignedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant leftAt,
        Instant deliveredAt,
        /** Quando entrou na fila (pedido pronto): o motoboy ve ha quanto tempo esta esperando. */
        Instant releasedAt,
        /** Localizacao do endereco (null = sem localizacao; aproximada quando addressHidden). */
        BigDecimal latitude,
        BigDecimal longitude,
        /** Motoboy que ainda nao pegou a entrega ve so o bairro (LGPD); rua e numero ao aceitar. */
        boolean addressHidden
) {}
