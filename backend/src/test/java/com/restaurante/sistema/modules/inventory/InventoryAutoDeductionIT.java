package com.restaurante.sistema.modules.inventory;

import com.restaurante.sistema.modules.catalog.dto.KitchenSectorResponse;
import com.restaurante.sistema.modules.dinein.dto.CommandResponse;
import com.restaurante.sistema.modules.dinein.dto.OpenServiceRequest;
import com.restaurante.sistema.modules.dinein.dto.ServiceResponse;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import com.restaurante.sistema.modules.inventory.dto.*;
import com.restaurante.sistema.modules.kitchen.dto.KitchenTaskResponse;
import com.restaurante.sistema.modules.ordering.dto.CreateOrderItemRequest;
import com.restaurante.sistema.modules.ordering.dto.CreateOrderRequest;
import com.restaurante.sistema.modules.ordering.dto.OrderResponse;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida a baixa automatica de estoque (Fase 2): cadastra um ingrediente,
 * associa uma ficha tecnica (Recipe) ao produto "X-Burguer Artesanal",
 * cria e leva um pedido ate a cozinha, conclui o item, e confirma que o
 * estoque foi baixado automaticamente na quantidade correta - sem nenhuma
 * chamada manual ao endpoint de baixa.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryAutoDeductionIT {

    private static final Long DEMO_TABLE_ID = 4L; // mesa "04" do seed, LIVRE
    private static final Long DEMO_PRODUCT_ID = 1L; // X-Burguer Artesanal, setor CHAPA

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
    private TestRestTemplate restTemplate;

    private HttpHeaders authHeaders() {
        LoginRequest login = new LoginRequest("admin@demo.local", "admin123");
        LoginResponse body = restTemplate.postForEntity("/api/auth/login", login, LoginResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void completingKitchenItemAutomaticallyDeductsStock() {
        HttpHeaders headers = authHeaders();

        // 1. Cadastrar ingrediente "Pao Brioche"
        InventoryItemResponse bunStock = restTemplate.exchange(
                "/api/inventory/items", HttpMethod.POST,
                new HttpEntity<>(new InventoryItemRequest(1L, "Pao Brioche", "UN", new BigDecimal("10"), new BigDecimal("1.50")), headers),
                InventoryItemResponse.class).getBody();

        // Ajusta o estoque inicial para 100 via movimento manual de compra
        restTemplate.exchange(
                "/api/inventory/items/" + bunStock.id() + "/movements", HttpMethod.POST,
                new HttpEntity<>(new StockMovementRequest("ENTRADA_COMPRA", new BigDecimal("100"), "Compra inicial"), headers),
                StockMovementResponse.class);

        // 2. Criar ficha tecnica: 1x X-Burguer Artesanal consome 1 unidade de Pao Brioche
        RecipeResponse recipe = restTemplate.exchange(
                "/api/inventory/recipes", HttpMethod.POST,
                new HttpEntity<>(new RecipeRequest(DEMO_PRODUCT_ID, List.of(new RecipeItemRequest(bunStock.id(), new BigDecimal("1")))), headers),
                RecipeResponse.class).getBody();
        assertThat(recipe.items()).hasSize(1);

        // 3. Abrir atendimento/comanda e pedir 3x X-Burguer Artesanal
        ServiceResponse service = restTemplate.exchange(
                "/api/services", HttpMethod.POST,
                new HttpEntity<>(new OpenServiceRequest(DEMO_TABLE_ID, 2, null), headers), ServiceResponse.class).getBody();
        CommandResponse command = restTemplate.exchange(
                "/api/commands?serviceId=" + service.id(), HttpMethod.POST,
                new HttpEntity<>(headers), CommandResponse.class).getBody();

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest(DEMO_PRODUCT_ID, null, 3, null, List.of());
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                1L, "SALAO", command.id(), null, null, null, List.of(itemRequest));
        OrderResponse order = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(orderRequest, headers), OrderResponse.class).getBody();

        // 4. Enviar para a cozinha, iniciar e concluir o item
        restTemplate.exchange("/api/orders/" + order.id() + "/send-to-kitchen", HttpMethod.POST,
                new HttpEntity<>(headers), OrderResponse.class);

        KitchenSectorResponse[] sectors = restTemplate.exchange(
                "/api/kitchen-sectors", HttpMethod.GET, new HttpEntity<>(headers), KitchenSectorResponse[].class).getBody();
        Long chapaSectorId = Arrays.stream(sectors).filter(s -> s.name().equals("CHAPA")).findFirst().orElseThrow().id();

        KitchenTaskResponse[] tasks = restTemplate.exchange(
                "/api/kitchen/tasks?sectorId=" + chapaSectorId, HttpMethod.GET,
                new HttpEntity<>(headers), KitchenTaskResponse[].class).getBody();
        Long orderItemId = tasks[0].orderItemId();

        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/start", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);
        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/complete", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);

        // 5. Confirmar baixa automatica: 100 - 3 = 97
        InventoryItemResponse[] itemsAfter = restTemplate.exchange(
                "/api/inventory/items?unitId=1", HttpMethod.GET, new HttpEntity<>(headers), InventoryItemResponse[].class).getBody();
        InventoryItemResponse afterDeduction = Arrays.stream(itemsAfter)
                .filter(i -> i.id().equals(bunStock.id())).findFirst().orElseThrow();

        assertThat(afterDeduction.currentQuantity()).isEqualByComparingTo("97");

        StockMovementResponse[] movements = restTemplate.exchange(
                "/api/inventory/items/" + bunStock.id() + "/movements", HttpMethod.GET,
                new HttpEntity<>(headers), StockMovementResponse[].class).getBody();
        assertThat(Arrays.stream(movements))
                .anyMatch(m -> m.type().equals("SAIDA_PRODUCAO") && m.quantity().compareTo(new BigDecimal("3")) == 0);
    }
}
