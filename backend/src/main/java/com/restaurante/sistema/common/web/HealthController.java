package com.restaurante.sistema.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint minimo para validar que a aplicacao subiu e esta conectada ao banco
 * (a propria inicializacao do Flyway ja falha o boot se o MySQL nao estiver acessivel).
 * Usado no docker-compose healthcheck e para verificacao manual apos "docker compose up".
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "service", "restaurante-sistema-backend"
        );
    }
}
