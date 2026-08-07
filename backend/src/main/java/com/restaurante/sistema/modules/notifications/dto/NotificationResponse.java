package com.restaurante.sistema.modules.notifications.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        Instant createdAt,
        boolean read
) {}
