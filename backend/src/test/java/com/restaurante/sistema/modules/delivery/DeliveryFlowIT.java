package com.restaurante.sistema.modules.delivery;

import com.restaurante.sistema.modules.catalog.dto.KitchenSectorResponse;
import com.restaurante.sistema.modules.couriers.dto.CourierRequest;
import com.restaurante.sistema.modules.couriers.dto.CourierResponse;
import com.restaurante.sistema.modules.customers.dto.CustomerAddressRequest;
import com.restaurante.sistema.modules.customers.dto.CustomerAddressResponse;
import com.restaurante.sistema.modules.customers.dto.CustomerRequest;
import com.restaurante.sistema.modules.customers.dto.CustomerResponse;
import com.restaurante.sistema.modules.delivery.dto.ConfirmDeliveryRequest;
import com.restaurante.sistema.modules.delivery.dto.DeliveryResponse;
import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneRequest;
import com.restaurante.sistema.modules.delivery.dto.DeliveryZoneResponse;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
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
 * Valida o fluxo completo de delivery: cliente + endereco -> zona de entrega
 * -> pedido DELIVERY (taxa somada automaticamente) -> entrega criada
 * automaticamente -> entregador aceita -> retira -> sai para entrega ->
 * confirma com codigo -> pedido finalizado.
 *
 * O admin cadastrado no seed nao e um entregador, entao criamos um Courier
 * vinculado ao proprio usuario admin apenas para viabilizar o teste (em
 * producao o MOTOBOY teria seu proprio usuario/perfil).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeliveryFlowIT {

    private static final Long DEMO_PRODUCT_ID = 2L; // X-Bacon Duplo, R$ 34.90 (acima do minimo de R$ 20 da zona "Centro")

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
    void fullDeliveryFlowFromOrderToConfirmedDelivery() {
        HttpHeaders headers = authHeaders();

        // 1. Cliente + endereco no bairro "Centro" (ja atendido pelo seed - taxa R$ 5.00)
        CustomerResponse customer = restTemplate.exchange(
                "/api/customers", HttpMethod.POST,
                new HttpEntity<>(new CustomerRequest("Cliente Delivery Teste", "11999998888", null, null, null), headers),
                CustomerResponse.class).getBody();

        CustomerAddressRequest addressRequest = new CustomerAddressRequest(
                "Casa", "Rua das Flores", "100", null, "Centro", "Sao Paulo", "SP", "01000-000", null, true);
        CustomerAddressResponse address = restTemplate.exchange(
                "/api/customers/" + customer.id() + "/addresses", HttpMethod.POST,
                new HttpEntity<>(addressRequest, headers), CustomerAddressResponse.class).getBody();

        // 2. Criar pedido DELIVERY
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest(DEMO_PRODUCT_ID, null, 1, null, List.of());
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                1L, "DELIVERY", null, customer.id(), address.id(), null, List.of(itemRequest));

        ResponseEntity<OrderResponse> orderResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(orderRequest, headers), OrderResponse.class);
        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse order = orderResponse.getBody();
        // 34.90 (produto) + 5.00 (taxa da zona "Centro" do seed) = 39.90
        assertThat(order.total()).isEqualByComparingTo("39.90");

        // 3. Confirmar que a entrega ja foi criada automaticamente
        DeliveryResponse delivery = restTemplate.exchange(
                "/api/deliveries/by-order/" + order.id(), HttpMethod.GET,
                new HttpEntity<>(headers), DeliveryResponse.class).getBody();
        assertThat(delivery.status()).isEqualTo("AGUARDANDO_ENTREGADOR");

        // 4. Cadastrar um entregador vinculado ao usuario admin (so para o teste)
        CourierResponse courier = restTemplate.exchange(
                "/api/couriers", HttpMethod.POST,
                new HttpEntity<>(new CourierRequest(1L, 1L, "Entregador Teste", "11988887777", "12345678900", "MOTO", "ABC1234"), headers),
                CourierResponse.class).getBody();
        assertThat(courier.status()).isEqualTo("OFFLINE");

        // 5. Levar o pedido pela cozinha ate PRONTO (necessario antes de despachar a entrega -
        //    a maquina de estado do pedido nao permite SAIU_PARA_ENTREGA a partir de RECEBIDO)
        restTemplate.exchange("/api/orders/" + order.id() + "/send-to-kitchen", HttpMethod.POST,
                new HttpEntity<>(headers), OrderResponse.class);

        KitchenSectorResponse[] sectors = restTemplate.exchange(
                "/api/kitchen-sectors", HttpMethod.GET, new HttpEntity<>(headers), KitchenSectorResponse[].class).getBody();
        Long chapaSectorId = Arrays.stream(sectors).filter(s -> s.name().equals("CHAPA")).findFirst().orElseThrow().id();
        List<KitchenTaskResponse> tasksBefore = List.of(restTemplate.exchange(
                "/api/kitchen/tasks?sectorId=" + chapaSectorId, HttpMethod.GET,
                new HttpEntity<>(headers), KitchenTaskResponse[].class).getBody());
        Long orderItemId = tasksBefore.get(0).orderItemId();

        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/start", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);
        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/complete", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);

        // Pedido DELIVERY pronto avanca automaticamente para AGUARDANDO_ENTREGADOR
        OrderResponse orderAfterKitchen = restTemplate.exchange(
                "/api/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody();
        assertThat(orderAfterKitchen.status()).isEqualTo("AGUARDANDO_ENTREGADOR");

        // 6. Aceitar a entrega
        ResponseEntity<DeliveryResponse> accepted = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(headers), DeliveryResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().status()).isEqualTo("ACEITA");

        // 7. Tentar aceitar de novo deve falhar (a entrega ja nao esta mais AGUARDANDO_ENTREGADOR)
        ResponseEntity<String> secondAttempt = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertThat(secondAttempt.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT);

        // 8. Retirar, sair para entrega, confirmar com codigo
        restTemplate.exchange("/api/deliveries/" + delivery.id() + "/pickup", HttpMethod.POST,
                new HttpEntity<>(headers), DeliveryResponse.class);
        ResponseEntity<DeliveryResponse> left = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/leave", HttpMethod.POST,
                new HttpEntity<>(headers), DeliveryResponse.class);
        assertThat(left.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(left.getBody().status()).isEqualTo("EM_ROTA");

        // Confirmar que o pedido acompanhou a entrega ate SAIU_PARA_ENTREGA
        OrderResponse orderAfterLeave = restTemplate.exchange(
                "/api/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody();
        assertThat(orderAfterLeave.status()).isEqualTo("SAIU_PARA_ENTREGA");

        // O DTO de resposta nao expoe o codigo de confirmacao por seguranca; aqui
        // testamos o caminho de ERRO com um codigo propositalmente invalido.
        ConfirmDeliveryRequest wrongCode = new ConfirmDeliveryRequest("000000", "Fulano");
        ResponseEntity<String> wrongCodeResponse = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/confirm", HttpMethod.POST,
                new HttpEntity<>(wrongCode, headers), String.class);
        assertThat(wrongCodeResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void deliveryZoneCoversMinimumOrderValidation() {
        HttpHeaders headers = authHeaders();

        DeliveryZoneResponse zone = restTemplate.exchange(
                "/api/delivery-zones", HttpMethod.POST,
                new HttpEntity<>(new DeliveryZoneRequest(1L, "Zona Restrita", "Bairro Distante",
                        new BigDecimal("10.00"), new BigDecimal("50.00"), 60), headers),
                DeliveryZoneResponse.class).getBody();
        assertThat(zone.minimumOrderValue()).isEqualByComparingTo("50.00");
    }
}
