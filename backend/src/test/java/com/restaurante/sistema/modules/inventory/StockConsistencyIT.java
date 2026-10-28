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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia do estoque no fluxo do guia (9.2): baixa na CONFIRMACAO,
 * estorno quando a cozinha marca o item como indisponivel, e recusa (422)
 * quando o saldo acaba entre a criacao e a confirmacao - sem saldo negativo.
 * Corpos em JSON simples (Map) para o teste nao quebrar quando um DTO ganha campos.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockConsistencyIT {

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

    /** Produto novo, so com um ingrediente, para o teste nao depender do seed. */
    private long[] productWithIngredient(HttpHeaders h, String name, int initialStock) {
        long itemId = call(HttpMethod.POST, "/api/inventory/items",
                Map.of("unitId", 1, "name", "Ingrediente " + name, "unitOfMeasure", "UN",
                        "minimumQuantity", 0, "costPerUnit", 1), h).getBody().get("id").asLong();
        call(HttpMethod.POST, "/api/inventory/items/" + itemId + "/movements",
                Map.of("type", "ENTRADA_COMPRA", "quantity", initialStock, "reason", "Saldo inicial"), h);

        long productId = call(HttpMethod.POST, "/api/products",
                Map.of("unitId", 1, "categoryId", 1, "name", name, "basePrice", 20,
                        "kitchenSectorId", 1, "available", false), h).getBody().get("id").asLong();
        call(HttpMethod.POST, "/api/inventory/recipes",
                Map.of("productId", productId, "yieldQuantity", 1,
                        "items", List.of(Map.of("inventoryItemId", itemId, "quantity", 1, "correctionFactor", 1))), h);
        call(HttpMethod.PATCH, "/api/products/" + productId + "/availability", Map.of("available", true), h);
        return new long[] {productId, itemId};
    }

    private BigDecimal stockOf(HttpHeaders h, long itemId) {
        for (JsonNode item : call(HttpMethod.GET, "/api/inventory/items?unitId=1", null, h).getBody()) {
            if (item.get("id").asLong() == itemId) {
                return item.get("currentQuantity").decimalValue();
            }
        }
        throw new AssertionError("Ingrediente " + itemId + " nao encontrado");
    }

    private ResponseEntity<JsonNode> createOrder(HttpHeaders h, long productId, int quantity) {
        return call(HttpMethod.POST, "/api/orders",
                Map.of("unitId", 1, "channel", "BALCAO",
                        "items", List.of(Map.of("productId", productId, "quantity", quantity))), h);
    }

    @Test
    void confirmingDeductsAndKitchenUnavailableReversesStock() {
        HttpHeaders h = admin();
        long[] ids = productWithIngredient(h, "Lanche Teste Estorno", 10);

        JsonNode order = createOrder(h, ids[0], 3).getBody();
        assertThat(order.get("status").asText()).isEqualTo("RECEBIDO");
        assertThat(stockOf(h, ids[1])).isEqualByComparingTo("10");

        ResponseEntity<JsonNode> confirmed = call(HttpMethod.POST,
                "/api/orders/" + order.get("id").asLong() + "/send-to-kitchen", null, h);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stockOf(h, ids[1])).isEqualByComparingTo("7");

        long orderItemId = order.get("items").get(0).get("id").asLong();
        ResponseEntity<JsonNode> unavailable = call(HttpMethod.POST,
                "/api/kitchen/tasks/" + orderItemId + "/unavailable", Map.of("reason", "Acabou"), h);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stockOf(h, ids[1])).isEqualByComparingTo("10");
    }

    @Test
    void confirmingWithoutStockIsRejectedAndNeverGoesNegative() {
        HttpHeaders h = admin();
        long[] ids = productWithIngredient(h, "Lanche Teste Saldo", 2);

        // Os dois pedidos passam pelo RN03 na criacao (saldo 2 >= 2 e >= 1)...
        long first = createOrder(h, ids[0], 2).getBody().get("id").asLong();
        long second = createOrder(h, ids[0], 1).getBody().get("id").asLong();

        // ...mas so o primeiro cabe no saldo na hora de confirmar.
        assertThat(call(HttpMethod.POST, "/api/orders/" + first + "/send-to-kitchen", null, h).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> rejected = call(HttpMethod.POST, "/api/orders/" + second + "/send-to-kitchen", null, h);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(stockOf(h, ids[1])).isEqualByComparingTo("0");
        JsonNode secondOrder = call(HttpMethod.GET, "/api/orders/" + second, null, h).getBody();
        assertThat(secondOrder.get("status").asText()).isEqualTo("RECEBIDO");
    }

    @Test
    void creatingOrderBeyondStockReturns422() {
        HttpHeaders h = admin();
        long[] ids = productWithIngredient(h, "Lanche Teste RN03", 1);

        ResponseEntity<JsonNode> response = createOrder(h, ids[0], 5);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("message").asText()).contains("Estoque insuficiente");
    }
}
