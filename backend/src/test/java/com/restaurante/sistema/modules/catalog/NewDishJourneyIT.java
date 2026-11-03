package com.restaurante.sistema.modules.catalog;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caminho completo de um prato novo, do cadastro ate a cozinha, como as telas fazem:
 * catalogo (nasce inativo) -> nao ativa sem setor nem ficha -> estoque + ficha tecnica ->
 * ativa -> aparece no cardapio publico -> cliente pede -> equipe confirma (baixa estoque)
 * -> item aparece no painel da cozinha do setor.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewDishJourneyIT {

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

    private HttpHeaders login(String email) {
        JsonNode body = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "admin123"), JsonNode.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.get("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String url, Object body, HttpHeaders headers) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    @Test
    void newDishGoesFromTheCatalogToTheCustomerAndTheKitchen() {
        HttpHeaders admin = login("admin@demo.local");

        // 1. Catalogo: prato novo, ainda sem setor
        Map<String, Object> dish = new HashMap<>(Map.of("unitId", 1, "categoryId", 1, "name", "Smash da Casa",
                "description", "Dois smash de 90g", "preparationMethod", "Prensado na chapa bem quente",
                "basePrice", 31.5, "available", false));
        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/api/products", dish, admin);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long productId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("available").asBoolean()).isFalse();

        // 2. Sem setor da cozinha nao ativa (sumiria do painel da cozinha)
        ResponseEntity<JsonNode> noSector = call(HttpMethod.PATCH, "/api/products/" + productId + "/availability",
                Map.of("available", true), admin);
        assertThat(noSector.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(noSector.getBody().get("message").asText()).contains("setor da cozinha");

        dish.put("kitchenSectorId", 1);
        assertThat(call(HttpMethod.PUT, "/api/products/" + productId, dish, admin).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Sem ficha tecnica tambem nao ativa (RN01)
        ResponseEntity<JsonNode> noRecipe = call(HttpMethod.PATCH, "/api/products/" + productId + "/availability",
                Map.of("available", true), admin);
        assertThat(noRecipe.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 4. Estoque + ficha tecnica
        long itemId = call(HttpMethod.POST, "/api/inventory/items", Map.of("unitId", 1, "name", "Blend Smash",
                "unitOfMeasure", "UN", "minimumQuantity", 0, "costPerUnit", 4), admin).getBody().get("id").asLong();
        call(HttpMethod.POST, "/api/inventory/items/" + itemId + "/movements",
                Map.of("type", "ENTRADA_COMPRA", "quantity", 10, "reason", "Compra inicial"), admin);
        ResponseEntity<JsonNode> recipe = call(HttpMethod.POST, "/api/inventory/recipes", Map.of("productId", productId,
                "yieldQuantity", 1, "items", List.of(Map.of("inventoryItemId", itemId, "quantity", 2, "correctionFactor", 1))), admin);
        assertThat(recipe.getStatusCode().is2xxSuccessful()).isTrue();

        // 5. Agora ativa e aparece no cardapio publico (sem login)
        assertThat(call(HttpMethod.PATCH, "/api/products/" + productId + "/availability", Map.of("available", true), admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode menu = rest.getForEntity("/api/products?unitId=1", JsonNode.class).getBody();
        boolean onMenu = false;
        for (JsonNode p : menu) if (p.get("id").asLong() == productId && p.get("available").asBoolean()) onMenu = true;
        assertThat(onMenu).isTrue();

        // 6. Cliente pede (retirada no balcao)
        HttpHeaders customer = login("cliente@demo.local");
        ResponseEntity<JsonNode> order = call(HttpMethod.POST, "/api/orders", Map.of("unitId", 1, "channel", "RETIRADA",
                "items", List.of(Map.of("productId", productId, "quantity", 2, "notes", "sem cebola"))), customer);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long orderId = order.getBody().get("id").asLong();
        assertThat(order.getBody().get("total").decimalValue()).isEqualByComparingTo("63.00");

        // 7. Equipe confirma: baixa 2 x 2 = 4 do estoque e manda para a cozinha
        ResponseEntity<JsonNode> sent = call(HttpMethod.POST, "/api/orders/" + orderId + "/send-to-kitchen", null, admin);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        double stock = 0;
        for (JsonNode i : call(HttpMethod.GET, "/api/inventory/items?unitId=1", null, admin).getBody()) {
            if (i.get("id").asLong() == itemId) stock = i.get("currentQuantity").asDouble();
        }
        assertThat(stock).isEqualTo(6.0);

        // 8. O item aparece no painel da cozinha do setor do prato, com a observacao do cliente
        boolean inKitchen = false;
        for (JsonNode t : call(HttpMethod.GET, "/api/kitchen/tasks?sectorId=1", null, admin).getBody()) {
            if (t.get("orderId").asLong() == orderId) {
                inKitchen = true;
                assertThat(t.get("notes").asText()).isEqualTo("sem cebola");
            }
        }
        assertThat(inKitchen).isTrue();
    }
}
