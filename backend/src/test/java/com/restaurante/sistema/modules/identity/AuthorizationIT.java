package com.restaurante.sistema.modules.identity;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autorizacao: sem token -> 401; cliente nao le dados da equipe (403) nem de
 * outro cliente (404); contas de demonstracao nao podem ser alteradas (422).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationIT {

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

    private HttpStatusCode status(HttpMethod method, String url, Object body, HttpHeaders headers) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        assertThat(rest.getForEntity("/api/orders?unitId=1", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void customerCannotReadStaffData() {
        HttpHeaders customer = login("cliente@demo.local");
        assertThat(status(HttpMethod.GET, "/api/orders?unitId=1", null, customer)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.GET, "/api/couriers/available?unitId=1", null, customer)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.GET, "/api/tables?unitId=1", null, customer)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.GET, "/api/customers/by-phone/000", null, customer)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.GET, "/api/users", null, customer)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerCannotReadAnotherCustomer() {
        HttpHeaders customer = login("cliente@demo.local");
        assertThat(status(HttpMethod.GET, "/api/customers/999999", null, customer)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(HttpMethod.GET, "/api/customers/me", null, customer)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void demoAccountsCannotBeChanged() {
        HttpHeaders admin = login("admin@demo.local");
        JsonNode users = rest.exchange("/api/users", HttpMethod.GET, new HttpEntity<>(admin), JsonNode.class).getBody();
        long managerId = -1;
        for (JsonNode u : users) {
            if ("gerente@demo.local".equals(u.get("email").asText())) {
                managerId = u.get("id").asLong();
            }
        }
        assertThat(managerId).isPositive();
        assertThat(status(HttpMethod.PATCH, "/api/users/" + managerId, Map.of("newPassword", "outraSenha123"), admin))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
