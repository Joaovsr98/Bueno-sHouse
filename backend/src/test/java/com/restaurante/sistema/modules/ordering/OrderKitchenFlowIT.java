package com.restaurante.sistema.modules.ordering;

import com.restaurante.sistema.modules.catalog.dto.KitchenSectorResponse;
import com.restaurante.sistema.modules.dinein.dto.CommandResponse;
import com.restaurante.sistema.modules.dinein.dto.OpenServiceRequest;
import com.restaurante.sistema.modules.dinein.dto.ServiceResponse;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import com.restaurante.sistema.modules.kitchen.dto.KitchenTaskResponse;
import com.restaurante.sistema.modules.ordering.dto.*;
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

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o fluxo completo pedido -> cozinha usando os dados do seed:
 * mesa "02" (id=2, LIVRE) -> abrir atendimento -> abrir comanda -> criar
 * pedido com 2 unidades de "X-Burguer Artesanal" (setor CHAPA) -> enviar
 * para a cozinha -> iniciar preparo do item -> concluir o item -> confirmar
 * que o pedido foi automaticamente para PRONTO.
 *
 * Tambem confirma a regra critica do projeto: o preco do item no pedido
 * bate com o preco do catalogo (28.90 x 2 = 57.80), mesmo que nunca tenha
 * sido enviado explicitamente pelo cliente no request.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderKitchenFlowIT {

    private static final Long DEMO_TABLE_ID = 2L; // mesa "02" do seed, status LIVRE
    private static final Long DEMO_PRODUCT_ID = 1L; // "X-Burguer Artesanal", R$ 28.90, setor CHAPA

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
    void fullOrderFlowFromCreationToKitchenReady() {
        HttpHeaders headers = authHeaders();

        // 1. Abrir atendimento + comanda
        ServiceResponse service = restTemplate.exchange(
                "/api/services", HttpMethod.POST,
                new HttpEntity<>(new OpenServiceRequest(DEMO_TABLE_ID, 2, null), headers),
                ServiceResponse.class).getBody();

        CommandResponse command = restTemplate.exchange(
                "/api/commands?serviceId=" + service.id(), HttpMethod.POST,
                new HttpEntity<>(headers), CommandResponse.class).getBody();

        // 2. Criar pedido com 2x X-Burguer Artesanal
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest(
                DEMO_PRODUCT_ID, null, 2, "Sem cebola", List.of());
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                1L, "SALAO", command.id(), null, null, "Pedido de teste", List.of(itemRequest));

        ResponseEntity<OrderResponse> orderResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(orderRequest, headers), OrderResponse.class);

        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse order = orderResponse.getBody();
        assertThat(order.status()).isEqualTo("RECEBIDO");
        // Regra critica: preco veio do catalogo (28.90), nunca do request
        assertThat(order.total()).isEqualByComparingTo("57.80");
        assertThat(order.items()).hasSize(1);
        Long orderItemId = order.items().get(0).id();

        // 3. Enviar para a cozinha
        ResponseEntity<OrderResponse> sentToKitchen = restTemplate.exchange(
                "/api/orders/" + order.id() + "/send-to-kitchen", HttpMethod.POST,
                new HttpEntity<>(headers), OrderResponse.class);
        assertThat(sentToKitchen.getBody().status()).isEqualTo("ENVIADO_PARA_COZINHA");
        assertThat(sentToKitchen.getBody().items().get(0).status()).isEqualTo("ENVIADO");

        // 4. Descobrir o setor CHAPA e iniciar o preparo do item
        KitchenSectorResponse[] sectors = restTemplate.exchange(
                "/api/kitchen-sectors", HttpMethod.GET, new HttpEntity<>(headers), KitchenSectorResponse[].class)
                .getBody();
        Long chapaSectorId = Arrays.stream(sectors).filter(s -> s.name().equals("CHAPA"))
                .findFirst().orElseThrow().id();

        ResponseEntity<KitchenTaskResponse[]> tasks = restTemplate.exchange(
                "/api/kitchen/tasks?sectorId=" + chapaSectorId, HttpMethod.GET,
                new HttpEntity<>(headers), KitchenTaskResponse[].class);
        assertThat(tasks.getBody()).isNotEmpty();

        ResponseEntity<KitchenTaskResponse> started = restTemplate.exchange(
                "/api/kitchen/tasks/" + orderItemId + "/start", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);
        assertThat(started.getBody().status()).isEqualTo("EM_PREPARO");

        // Pedido deve ter avancado automaticamente para EM_PREPARO
        OrderResponse afterStart = restTemplate.exchange(
                "/api/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody();
        assertThat(afterStart.status()).isEqualTo("EM_PREPARO");

        // 5. Concluir o item -> pedido deve ir para PRONTO (unico item, sem mais nada pendente)
        ResponseEntity<KitchenTaskResponse> completed = restTemplate.exchange(
                "/api/kitchen/tasks/" + orderItemId + "/complete", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);
        assertThat(completed.getBody().status()).isEqualTo("PRONTO");

        OrderResponse finalOrder = restTemplate.exchange(
                "/api/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody();
        assertThat(finalOrder.status()).isEqualTo("PRONTO");
    }
}
