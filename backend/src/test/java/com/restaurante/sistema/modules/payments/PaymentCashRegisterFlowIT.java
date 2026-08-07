package com.restaurante.sistema.modules.payments;

import com.restaurante.sistema.modules.cashregister.dto.CashRegisterResponse;
import com.restaurante.sistema.modules.cashregister.dto.CloseCashRegisterRequest;
import com.restaurante.sistema.modules.cashregister.dto.OpenCashRegisterRequest;
import com.restaurante.sistema.modules.dinein.dto.CommandResponse;
import com.restaurante.sistema.modules.dinein.dto.OpenServiceRequest;
import com.restaurante.sistema.modules.dinein.dto.ServiceResponse;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import com.restaurante.sistema.modules.ordering.dto.CreateOrderItemRequest;
import com.restaurante.sistema.modules.ordering.dto.CreateOrderRequest;
import com.restaurante.sistema.modules.ordering.dto.OrderResponse;
import com.restaurante.sistema.modules.payments.dto.OrderBalanceResponse;
import com.restaurante.sistema.modules.payments.dto.PaymentResponse;
import com.restaurante.sistema.modules.payments.dto.RegisterPaymentRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o ciclo completo: abrir caixa -> abrir atendimento/comanda -> criar
 * pedido -> pagar em dinheiro o valor exato -> confirmar que a comanda fecha
 * automaticamente -> fechar o caixa e conferir que o valor recebido em
 * dinheiro entrou corretamente no saldo esperado.
 *
 * Usa a mesa "03" (id=3, LIVRE no seed) para nao colidir com as mesas
 * usadas em DineinControllerIT (id=1) e OrderKitchenFlowIT (id=2).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCashRegisterFlowIT {

    private static final Long DEMO_TABLE_ID = 3L;
    private static final Long DEMO_PRODUCT_ID = 3L; // Refrigerante Lata 350ml, R$ 7.00

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
    void fullPaymentFlowClosesCommandAndReconciliatesCashRegister() {
        HttpHeaders headers = authHeaders();

        // 1. Abrir caixa
        CashRegisterResponse register = restTemplate.exchange(
                "/api/cash-registers", HttpMethod.POST,
                new HttpEntity<>(new OpenCashRegisterRequest(1L, new BigDecimal("100.00")), headers),
                CashRegisterResponse.class).getBody();
        assertThat(register.open()).isTrue();

        // 2. Abrir atendimento + comanda
        ServiceResponse service = restTemplate.exchange(
                "/api/services", HttpMethod.POST,
                new HttpEntity<>(new OpenServiceRequest(DEMO_TABLE_ID, 1, null), headers),
                ServiceResponse.class).getBody();

        CommandResponse command = restTemplate.exchange(
                "/api/commands?serviceId=" + service.id(), HttpMethod.POST,
                new HttpEntity<>(headers), CommandResponse.class).getBody();

        // 3. Criar pedido de 3x refrigerante = 21.00
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest(DEMO_PRODUCT_ID, null, 3, null, List.of());
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                1L, "SALAO", command.id(), null, null, null, List.of(itemRequest));

        OrderResponse order = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(orderRequest, headers), OrderResponse.class).getBody();
        assertThat(order.total()).isEqualByComparingTo("21.00");

        // 4. Verificar saldo devedor antes do pagamento
        OrderBalanceResponse balanceBefore = restTemplate.exchange(
                "/api/payments/balance?orderId=" + order.id(), HttpMethod.GET,
                new HttpEntity<>(headers), OrderBalanceResponse.class).getBody();
        assertThat(balanceBefore.fullyPaid()).isFalse();
        assertThat(balanceBefore.remaining()).isEqualByComparingTo("21.00");

        // 5. Pagar o valor exato em dinheiro
        RegisterPaymentRequest paymentRequest = new RegisterPaymentRequest(
                order.id(), "DINHEIRO", new BigDecimal("21.00"), register.id());
        ResponseEntity<PaymentResponse> paymentResponse = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, new HttpEntity<>(paymentRequest, headers), PaymentResponse.class);
        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(paymentResponse.getBody().status()).isEqualTo("APROVADO");

        // 6. Confirmar saldo zerado
        OrderBalanceResponse balanceAfter = restTemplate.exchange(
                "/api/payments/balance?orderId=" + order.id(), HttpMethod.GET,
                new HttpEntity<>(headers), OrderBalanceResponse.class).getBody();
        assertThat(balanceAfter.fullyPaid()).isTrue();
        assertThat(balanceAfter.remaining()).isEqualByComparingTo("0.00");

        // 7. Tentar pagar um valor que excede o saldo (ja pago) deve falhar
        RegisterPaymentRequest overpayRequest = new RegisterPaymentRequest(
                order.id(), "DINHEIRO", new BigDecimal("1.00"), register.id());
        ResponseEntity<String> overpayResponse = restTemplate.exchange(
                "/api/payments", HttpMethod.POST, new HttpEntity<>(overpayRequest, headers), String.class);
        assertThat(overpayResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 8. Fechar o caixa - saldo esperado deve ser 100 (abertura) + 21 (pagamento em dinheiro) = 121
        CashRegisterResponse closed = restTemplate.exchange(
                "/api/cash-registers/" + register.id() + "/close", HttpMethod.POST,
                new HttpEntity<>(new CloseCashRegisterRequest(new BigDecimal("121.00")), headers),
                CashRegisterResponse.class).getBody();

        assertThat(closed.expectedBalance()).isEqualByComparingTo("121.00");
        assertThat(closed.difference()).isEqualByComparingTo("0.00");
        assertThat(closed.open()).isFalse();
    }
}
