package com.restaurante.sistema.modules.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fornecedores e compras com os dados de exemplo (V904): compras recebidas geram
 * historico de precos (RF-026), ha pedidos de compra em RASCUNHO e ENVIADO para a
 * demonstracao, fornecedor desativado nao recebe compra nova e o resumo do dia
 * (RF-034) responde. Corpos em JSON simples para nao quebrar quando um DTO cresce.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupplierPurchasesIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("restaurante_db")
            .withUsername("restaurante_app")
            .withPassword("test_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders admin() {
        JsonNode body = rest.postForEntity("/api/auth/login",
                Map.of("email", "admin@demo.local", "password", "admin123"), JsonNode.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.get("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String url, Object body, HttpHeaders headers) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private long supplierId(HttpHeaders h, String name) {
        for (JsonNode s : call(HttpMethod.GET, "/api/suppliers?unitId=1", null, h).getBody()) {
            if (s.get("name").asText().equals(name)) return s.get("id").asLong();
        }
        throw new AssertionError("Fornecedor do seed nao encontrado: " + name);
    }

    @Test
    void seedHasPriceHistoryAndPurchaseOrdersInEveryStage() {
        HttpHeaders h = admin();

        ResponseEntity<JsonNode> history = call(HttpMethod.GET,
                "/api/suppliers/" + supplierId(h, "Atacadao Bom Preco") + "/price-history", null, h);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).isNotEmpty();

        List<String> statuses = new ArrayList<>();
        call(HttpMethod.GET, "/api/purchases?unitId=1", null, h).getBody()
                .forEach(p -> statuses.add(p.get("status").asText()));
        assertThat(statuses).contains("RASCUNHO", "ENVIADO", "RECEBIDO");
    }

    @Test
    void deactivatedSupplierCannotReceiveNewPurchases() {
        HttpHeaders h = admin();
        long id = supplierId(h, "Distribuidora Sabor Real");

        ResponseEntity<JsonNode> off = call(HttpMethod.PATCH, "/api/suppliers/" + id + "/active", Map.of("active", false), h);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(off.getBody().get("active").asBoolean()).isFalse();

        long itemId = call(HttpMethod.GET, "/api/inventory/items?unitId=1", null, h).getBody().get(0).get("id").asLong();
        ResponseEntity<JsonNode> purchase = call(HttpMethod.POST, "/api/purchases",
                Map.of("unitId", 1, "supplierId", id,
                        "items", List.of(Map.of("inventoryItemId", itemId, "quantity", 1, "unitPrice", 10))), h);
        assertThat(purchase.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        call(HttpMethod.PATCH, "/api/suppliers/" + id + "/active", Map.of("active", true), h);
    }

    @Test
    void todaySummaryAnswers() {
        ResponseEntity<JsonNode> today = call(HttpMethod.GET, "/api/reports/today?unitId=1", null, admin());

        assertThat(today.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(today.getBody().has("revenue")).isTrue();
        assertThat(today.getBody().has("averageTicket")).isTrue();
    }
}
