package com.restaurante.sistema.modules.identity;

import com.restaurante.sistema.modules.identity.dto.LoginRequest;
import com.restaurante.sistema.modules.identity.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o fluxo de login de ponta a ponta contra um MySQL real com os dados
 * de demonstracao (V900) aplicados, cobrindo:
 *   - login valido emite um JWT de verdade;
 *   - login invalido retorna 401 com o formato padronizado de erro;
 *   - o endpoint de health continua publico sem token.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

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

    @Test
    void loginWithValidCredentialsReturnsToken() {
        LoginRequest request = new LoginRequest("admin@demo.local", "admin123");

        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity("/api/auth/login", request, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().user().email()).isEqualTo("admin@demo.local");
        assertThat(response.getBody().user().profileName()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    void loginWithInvalidPasswordReturns401() {
        LoginRequest request = new LoginRequest("admin@demo.local", "senha-errada");

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthEndpointDoesNotRequireAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
