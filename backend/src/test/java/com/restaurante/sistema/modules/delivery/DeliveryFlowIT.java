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

    private static final Long DEMO_PRODUCT_ID = 2L; // X-Bacon Duplo, R$ 34.90 (acima do minimo de R$ 25 da zona "Centro")

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
        return authHeadersAs("admin@demo.local");
    }

    private HttpHeaders authHeadersAs(String email) {
        LoginRequest login = new LoginRequest(email, "admin123");
        LoginResponse body = restTemplate.postForEntity("/api/auth/login", login, LoginResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void addressWithInvalidZipCodeIsRejectedWith400() {
        HttpHeaders headers = authHeaders();
        CustomerResponse customer = restTemplate.exchange(
                "/api/customers", HttpMethod.POST,
                new HttpEntity<>(new CustomerRequest("Cliente CEP Teste", "11999997777", null, null, null), headers),
                CustomerResponse.class).getBody();

        CustomerAddressRequest invalid = new CustomerAddressRequest(
                "Casa", "Rua das Flores", "100", null, "Centro", "Sao Paulo", "SP", "abc", null, true);
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/customers/" + customer.id() + "/addresses", HttpMethod.POST,
                new HttpEntity<>(invalid, headers), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Sem hifen e aceito e gravado no formato 00000-000.
        CustomerAddressRequest digitsOnly = new CustomerAddressRequest(
                "Casa", "Rua das Flores", "100", null, "Centro", "Sao Paulo", "SP", "01001000", null, true);
        CustomerAddressResponse saved = restTemplate.exchange(
                "/api/customers/" + customer.id() + "/addresses", HttpMethod.POST,
                new HttpEntity<>(digitsOnly, headers), CustomerAddressResponse.class).getBody();
        assertThat(saved.zipCode()).isEqualTo("01001-000");
    }

    @Test
    void fullDeliveryFlowFromOrderToConfirmedDelivery() {
        HttpHeaders headers = authHeaders();

        // 1. Cliente + endereco no bairro "Centro" (ja atendido pelo seed - taxa R$ 5.90, ver V902)
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
        // 34.90 (produto) + 5.90 (taxa da zona "Centro", seed V902) = 40.80
        assertThat(order.total()).isEqualByComparingTo("40.80");
        // Fluxo 9.1/9.2 do guia: o pedido nasce RECEBIDO e espera a confirmacao da equipe
        assertThat(order.status()).isEqualTo("RECEBIDO");

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
        //    a maquina de estado do pedido nao permite SAIU_PARA_ENTREGA antes disso)
        restTemplate.exchange("/api/orders/" + order.id() + "/send-to-kitchen", HttpMethod.POST,
                new HttpEntity<>(headers), OrderResponse.class);
        KitchenSectorResponse[] sectors = restTemplate.exchange(
                "/api/kitchen-sectors", HttpMethod.GET, new HttpEntity<>(headers), KitchenSectorResponse[].class).getBody();
        Long chapaSectorId = Arrays.stream(sectors).filter(s -> s.name().equals("CHAPA")).findFirst().orElseThrow().id();
        List<KitchenTaskResponse> tasksBefore = List.of(restTemplate.exchange(
                "/api/kitchen/tasks?sectorId=" + chapaSectorId, HttpMethod.GET,
                new HttpEntity<>(headers), KitchenTaskResponse[].class).getBody());
        // A fila da chapa pode ter outros pedidos (seed de demonstracao): pega a tarefa deste pedido.
        Long orderItemId = tasksBefore.stream()
                .filter(t -> t.orderId().equals(order.id()))
                .findFirst().orElseThrow().orderItemId();

        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/start", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);
        restTemplate.exchange("/api/kitchen/tasks/" + orderItemId + "/complete", HttpMethod.POST,
                new HttpEntity<>(headers), KitchenTaskResponse.class);

        // Pedido DELIVERY pronto avanca automaticamente para AGUARDANDO_ENTREGADOR
        OrderResponse orderAfterKitchen = restTemplate.exchange(
                "/api/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class).getBody();
        assertThat(orderAfterKitchen.status()).isEqualTo("AGUARDANDO_ENTREGADOR");

        // 6. Aceitar a entrega - so o perfil MOTOBOY aceita (entregador do seed vinculado a motoboy@demo.local)
        HttpHeaders motoboy = authHeadersAs("motoboy@demo.local");

        // Antes de aceitar, o motoboy ve o bairro; rua e numero so depois de pegar (LGPD)
        DeliveryResponse[] pool = restTemplate.exchange("/api/deliveries/available/details", HttpMethod.GET,
                new HttpEntity<>(motoboy), DeliveryResponse[].class).getBody();
        assertThat(pool).anySatisfy(d -> {
            assertThat(d.id()).isEqualTo(delivery.id());
            assertThat(d.addressHidden()).isTrue();
            assertThat(d.addressSnapshot()).isEqualTo("Centro");
        });

        ResponseEntity<DeliveryResponse> accepted = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(motoboy), DeliveryResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().status()).isEqualTo("ACEITA");
        assertThat(accepted.getBody().addressHidden()).isFalse();
        assertThat(accepted.getBody().addressSnapshot()).contains("Rua das Flores");

        // Recarregar a pagina nao perde a entrega: ela volta em "minhas entregas"
        DeliveryResponse[] mine = restTemplate.exchange("/api/deliveries/mine", HttpMethod.GET,
                new HttpEntity<>(motoboy), DeliveryResponse[].class).getBody();
        assertThat(mine).extracting(DeliveryResponse::id).contains(delivery.id());

        // 7. Tentar aceitar de novo deve falhar (a entrega ja nao esta mais AGUARDANDO_ENTREGADOR)
        ResponseEntity<String> secondAttempt = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(motoboy), String.class);
        assertThat(secondAttempt.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT);

        // 8. Retirar, sair para entrega, confirmar com codigo
        restTemplate.exchange("/api/deliveries/" + delivery.id() + "/pickup", HttpMethod.POST,
                new HttpEntity<>(motoboy), DeliveryResponse.class);
        ResponseEntity<DeliveryResponse> left = restTemplate.exchange(
                "/api/deliveries/" + delivery.id() + "/leave", HttpMethod.POST,
                new HttpEntity<>(motoboy), DeliveryResponse.class);
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
                new HttpEntity<>(wrongCode, motoboy), String.class);
        assertThat(wrongCodeResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Pedido DELIVERY recem-criado (RECEBIDO) para o bairro Centro. */
    private OrderResponse deliveryOrder(HttpHeaders admin, String phone) {
        CustomerResponse customer = restTemplate.exchange("/api/customers", HttpMethod.POST,
                new HttpEntity<>(new CustomerRequest("Cliente Fila " + phone, phone, null, null, null), admin),
                CustomerResponse.class).getBody();
        CustomerAddressResponse address = restTemplate.exchange(
                "/api/customers/" + customer.id() + "/addresses", HttpMethod.POST,
                new HttpEntity<>(new CustomerAddressRequest("Casa", "Rua das Flores", "100", null, "Centro",
                        "Sao Paulo", "SP", "01000-000", null, true), admin), CustomerAddressResponse.class).getBody();
        OrderResponse order = restTemplate.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>(
                new CreateOrderRequest(1L, "DELIVERY", null, customer.id(), address.id(), null,
                        List.of(new CreateOrderItemRequest(DEMO_PRODUCT_ID, null, 1, null, List.of()))), admin),
                OrderResponse.class).getBody();
        return order;
    }

    /** Pedido DELIVERY levado ate pronto (cozinha concluida); devolve a entrega. */
    private DeliveryResponse readyDelivery(HttpHeaders admin, String phone) {
        OrderResponse order = deliveryOrder(admin, phone);

        // Ainda na cozinha: nao esta na fila do motoboy
        DeliveryResponse[] poolBefore = restTemplate.exchange("/api/deliveries/available/details", HttpMethod.GET,
                new HttpEntity<>(admin), DeliveryResponse[].class).getBody();
        assertThat(poolBefore).noneMatch(d -> d.orderId().equals(order.id()));

        restTemplate.exchange("/api/orders/" + order.id() + "/send-to-kitchen", HttpMethod.POST,
                new HttpEntity<>(admin), OrderResponse.class);
        KitchenSectorResponse[] sectors = restTemplate.exchange("/api/kitchen-sectors", HttpMethod.GET,
                new HttpEntity<>(admin), KitchenSectorResponse[].class).getBody();
        for (KitchenSectorResponse sector : sectors) {
            for (KitchenTaskResponse task : restTemplate.exchange("/api/kitchen/tasks?sectorId=" + sector.id(),
                    HttpMethod.GET, new HttpEntity<>(admin), KitchenTaskResponse[].class).getBody()) {
                if (!task.orderId().equals(order.id())) continue;
                restTemplate.exchange("/api/kitchen/tasks/" + task.orderItemId() + "/start", HttpMethod.POST,
                        new HttpEntity<>(admin), KitchenTaskResponse.class);
                restTemplate.exchange("/api/kitchen/tasks/" + task.orderItemId() + "/complete", HttpMethod.POST,
                        new HttpEntity<>(admin), KitchenTaskResponse.class);
            }
        }
        return restTemplate.exchange("/api/deliveries/by-order/" + order.id(), HttpMethod.GET,
                new HttpEntity<>(admin), DeliveryResponse.class).getBody();
    }

    @Test
    void courierTakesTheOldestReadyDeliveryFirstAndThenCanTakeOthersAlong() {
        HttpHeaders admin = authHeaders();

        // Motoboy so deste teste (o do seed pode estar com entrega em maos de outro teste)
        restTemplate.exchange("/api/users", HttpMethod.POST, new HttpEntity<>(
                java.util.Map.of("email", "motoboy.fila@teste.local", "password", "senhaFila123", "profileName", "MOTOBOY"),
                admin), String.class);
        Long userId = java.util.Arrays.stream(restTemplate.exchange("/api/users", HttpMethod.GET, new HttpEntity<>(admin),
                        com.restaurante.sistema.modules.identity.dto.UserResponse[].class).getBody())
                .filter(u -> u.email().equals("motoboy.fila@teste.local")).findFirst().orElseThrow().id();
        restTemplate.exchange("/api/couriers", HttpMethod.POST, new HttpEntity<>(
                new CourierRequest(1L, userId, "Motoboy da Fila", "11977776666", "98765432100", "MOTO", "FIL1A23"), admin),
                CourierResponse.class);
        LoginResponse login = restTemplate.postForEntity("/api/auth/login",
                new LoginRequest("motoboy.fila@teste.local", "senhaFila123"), LoginResponse.class).getBody();
        HttpHeaders courier = new HttpHeaders();
        courier.setBearerAuth(login.token());
        courier.setContentType(MediaType.APPLICATION_JSON);

        DeliveryResponse older = readyDelivery(admin, "11999996661");
        DeliveryResponse newer = readyDelivery(admin, "11999996662");
        assertThat(older.releasedAt()).isNotNull();

        // Sem entrega em maos: a mais nova esta travada, a primeira da fila e a mais antiga
        ResponseEntity<String> skipQueue = restTemplate.exchange("/api/deliveries/" + newer.id() + "/accept",
                HttpMethod.POST, new HttpEntity<>(courier), String.class);
        assertThat(skipQueue.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(skipQueue.getBody()).contains("#" + older.id());

        assertThat(restTemplate.exchange("/api/deliveries/" + older.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(courier), DeliveryResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Com uma em maos, pode levar a outra junto
        assertThat(restTemplate.exchange("/api/deliveries/" + newer.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(courier), DeliveryResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        DeliveryResponse[] mine = restTemplate.exchange("/api/deliveries/mine", HttpMethod.GET,
                new HttpEntity<>(courier), DeliveryResponse[].class).getBody();
        assertThat(mine).extracting(DeliveryResponse::id).containsExactlyInAnyOrder(older.id(), newer.id());

        // Outro motoboy nao mexe na entrega de quem pegou (so o dono, o gerente ou o admin)
        assertThat(restTemplate.exchange("/api/deliveries/" + older.id() + "/pickup", HttpMethod.POST,
                new HttpEntity<>(authHeadersAs("motoboy@demo.local")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange("/api/deliveries/" + older.id() + "/pickup", HttpMethod.POST,
                new HttpEntity<>(courier), DeliveryResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelledDeliveryOrderCancelsItsDelivery() {
        HttpHeaders admin = authHeaders();
        OrderResponse order = deliveryOrder(admin, "11999996663");

        ResponseEntity<String> cancel = restTemplate.exchange("/api/orders/" + order.id() + "/transitions",
                HttpMethod.POST, new HttpEntity<>(java.util.Map.of("status", "CANCELADO", "reason", "Cliente desistiu"), admin),
                String.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);

        DeliveryResponse after = restTemplate.exchange("/api/deliveries/by-order/" + order.id(), HttpMethod.GET,
                new HttpEntity<>(admin), DeliveryResponse.class).getBody();
        assertThat(after.status()).isEqualTo("CANCELADA");
        assertThat(after.releasedAt()).isNull();
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
