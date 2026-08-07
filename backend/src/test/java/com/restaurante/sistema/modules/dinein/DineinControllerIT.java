package com.restaurante.sistema.modules.dinein;

import com.restaurante.sistema.modules.dinein.dto.*;
import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o fluxo completo de salao usando a mesa "01" (id=1) do seed
 * (V900__seed_demo_data.sql, status inicial LIVRE):
 *
 *   abrir atendimento (mesa -> OCUPADA)
 *   -> abrir comanda (atendimento -> IN_SERVICE)
 *   -> transicionar comanda ate FECHADA
 *   -> encerrar atendimento (mesa -> AGUARDANDO_LIMPEZA)
 *
 * Tambem confirma a invariante central da Etapa 2/3: uma mesa nao pode ter
 * dois atendimentos ativos simultaneamente.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DineinControllerIT {

    private static final Long DEMO_TABLE_ID = 1L; // mesa "01" do seed, status LIVRE

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
    void fullDineinFlowFromOpenServiceToTableAwaitingCleaning() {
        HttpHeaders headers = authHeaders();

        // 1. Abrir atendimento
        OpenServiceRequest openRequest = new OpenServiceRequest(DEMO_TABLE_ID, 2, "Cliente de teste");
        ResponseEntity<ServiceResponse> serviceResponse = restTemplate.exchange(
                "/api/services", HttpMethod.POST, new HttpEntity<>(openRequest, headers), ServiceResponse.class);

        assertThat(serviceResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(serviceResponse.getBody().status()).isEqualTo("OPEN");
        Long serviceId = serviceResponse.getBody().id();

        // Mesa deve estar OCUPADA agora
        ResponseEntity<TableResponse[]> tables = restTemplate.exchange(
                "/api/tables?unitId=1", HttpMethod.GET, new HttpEntity<>(headers), TableResponse[].class);
        TableResponse table = java.util.Arrays.stream(tables.getBody())
                .filter(t -> t.id().equals(DEMO_TABLE_ID)).findFirst().orElseThrow();
        assertThat(table.status()).isEqualTo("OCUPADA");

        // 2. Tentar abrir um SEGUNDO atendimento na mesma mesa deve falhar (422)
        ResponseEntity<String> duplicateAttempt = restTemplate.exchange(
                "/api/services", HttpMethod.POST, new HttpEntity<>(openRequest, headers), String.class);
        assertThat(duplicateAttempt.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // 3. Abrir comanda (deve avancar o atendimento para IN_SERVICE)
        ResponseEntity<CommandResponse> commandResponse = restTemplate.exchange(
                "/api/commands?serviceId=" + serviceId, HttpMethod.POST,
                new HttpEntity<>(headers), CommandResponse.class);
        assertThat(commandResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long commandId = commandResponse.getBody().id();

        // 4. Levar a comanda ate FECHADA
        transitionCommand(commandId, "EM_ATENDIMENTO", headers);
        transitionCommand(commandId, "AGUARDANDO_PAGAMENTO", headers);
        ResponseEntity<CommandResponse> closed = transitionCommand(commandId, "FECHADA", headers);
        assertThat(closed.getBody().status()).isEqualTo("FECHADA");

        // 5. Encerrar o atendimento -> mesa deve ir para AGUARDANDO_LIMPEZA
        ResponseEntity<ServiceResponse> closeService = restTemplate.exchange(
                "/api/services/" + serviceId + "/transitions", HttpMethod.POST,
                new HttpEntity<>(new TransitionRequest("AWAITING_CLOSURE"), headers), ServiceResponse.class);
        assertThat(closeService.getBody().status()).isEqualTo("AWAITING_CLOSURE");

        ResponseEntity<ServiceResponse> finalClose = restTemplate.exchange(
                "/api/services/" + serviceId + "/transitions", HttpMethod.POST,
                new HttpEntity<>(new TransitionRequest("CLOSED"), headers), ServiceResponse.class);
        assertThat(finalClose.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalClose.getBody().status()).isEqualTo("CLOSED");

        ResponseEntity<TableResponse[]> tablesAfter = restTemplate.exchange(
                "/api/tables?unitId=1", HttpMethod.GET, new HttpEntity<>(headers), TableResponse[].class);
        TableResponse tableAfter = java.util.Arrays.stream(tablesAfter.getBody())
                .filter(t -> t.id().equals(DEMO_TABLE_ID)).findFirst().orElseThrow();
        assertThat(tableAfter.status()).isEqualTo("AGUARDANDO_LIMPEZA");
    }

    private ResponseEntity<CommandResponse> transitionCommand(Long commandId, String status, HttpHeaders headers) {
        return restTemplate.exchange(
                "/api/commands/" + commandId + "/transitions", HttpMethod.POST,
                new HttpEntity<>(new TransitionRequest(status), headers), CommandResponse.class);
    }
}
