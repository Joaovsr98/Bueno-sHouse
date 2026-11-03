package com.restaurante.sistema.modules.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurante.sistema.modules.customers.dto.CustomerRequest;
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
 * "Restaurar demonstracao": so o administrador pede, digitando RESTAURAR, e o banco volta ao
 * estado dos seeds (o que foi criado nos testes some, os usuarios de exemplo continuam entrando).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.demo-reset.enabled=true", "spring.flyway.clean-disabled=false"})
class DemoResetIT {

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

    @Autowired
    private DemoResetService demoReset;

    private HttpHeaders login(String email) {
        JsonNode body = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "admin123"), JsonNode.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.get("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpStatusCode post(String url, Object body, HttpHeaders headers) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    @Test
    void onlyTheAdministratorCanAskAndMustTypeTheConfirmation() {
        assertThat(post("/api/admin/demo/reset", Map.of("confirm", "RESTAURAR"), login("gerente@demo.local")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/admin/demo/reset", Map.of("confirm", "sim"), login("admin@demo.local")))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // Status e publico: durante a restauracao nao ha como logar
        assertThat(rest.getForEntity("/api/demo/reset-status", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restoreRemovesWhatWasCreatedAndKeepsTheDemoData() {
        HttpHeaders admin = login("admin@demo.local");
        rest.exchange("/api/customers", HttpMethod.POST, new HttpEntity<>(
                new CustomerRequest("Cliente Para Apagar", "11955554444", null, null, null), admin), String.class);
        assertThat(rest.exchange("/api/customers/by-phone/11955554444", HttpMethod.GET, new HttpEntity<>(admin),
                String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        demoReset.restoreNow();

        HttpHeaders adminAgain = login("admin@demo.local");
        assertThat(rest.exchange("/api/customers/by-phone/11955554444", HttpMethod.GET, new HttpEntity<>(adminAgain),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/products?unitId=1", JsonNode.class).getBody()).isNotEmpty();
        assertThat(demoReset.status().running()).isFalse();
        assertThat(demoReset.status().error()).isNull();
    }

    @Test
    void adminRequestRunsInTheBackgroundAndTheStatusShowsWhenItEnds() throws InterruptedException {
        assertThat(post("/api/admin/demo/reset", Map.of("confirm", "RESTAURAR"), login("admin@demo.local")))
                .isEqualTo(HttpStatus.ACCEPTED);

        JsonNode status = null;
        for (int i = 0; i < 120; i++) {
            status = rest.getForEntity("/api/demo/reset-status", JsonNode.class).getBody();
            if (!status.get("running").asBoolean()) break;
            Thread.sleep(500);
        }
        assertThat(status.get("running").asBoolean()).isFalse();
        assertThat(status.get("finishedAt").isNull()).isFalse();
        assertThat(login("admin@demo.local").getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
    }
}
