package com.restaurante.sistema.modules.catalog;

import com.restaurante.sistema.modules.catalog.dto.CategoryRequest;
import com.restaurante.sistema.modules.catalog.dto.CategoryResponse;
import com.restaurante.sistema.modules.catalog.dto.ProductRequest;
import com.restaurante.sistema.modules.catalog.dto.ProductResponse;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o modulo catalog de ponta a ponta: login -> criar categoria
 * autenticado -> criar produto vinculado a essa categoria -> listar produtos
 * da unidade de demonstracao (que ja tem categorias/produtos do V900).
 * Tambem confirma que criar categoria sem token retorna 401 (regra da
 * Etapa 5: tudo exige autenticacao por padrao).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogControllerIT {

    private static final Long DEMO_UNIT_ID = 1L;

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
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/api/auth/login", login, LoginResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void createCategoryWithoutTokenReturns401() {
        CategoryRequest request = new CategoryRequest(DEMO_UNIT_ID, "Sem token", 1);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/categories", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createCategoryAndProductAsAdministrador() {
        HttpHeaders headers = authHeaders();

        CategoryRequest categoryRequest = new CategoryRequest(DEMO_UNIT_ID, "Combos", 4);
        ResponseEntity<CategoryResponse> categoryResponse = restTemplate.exchange(
                "/api/categories", HttpMethod.POST,
                new HttpEntity<>(categoryRequest, headers), CategoryResponse.class);

        assertThat(categoryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long categoryId = categoryResponse.getBody().id();

        ProductRequest productRequest = new ProductRequest(
                DEMO_UNIT_ID, categoryId, "Combo Teste", "Descricao de teste",
                new BigDecimal("39.90"), null, 15, null, true, false, List.of(), null
        );

        ResponseEntity<ProductResponse> productResponse = restTemplate.exchange(
                "/api/products", HttpMethod.POST,
                new HttpEntity<>(productRequest, headers), ProductResponse.class);

        assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(productResponse.getBody().name()).isEqualTo("Combo Teste");
        assertThat(productResponse.getBody().basePrice()).isEqualByComparingTo("39.90");
    }

    @Test
    void listProductsFromDemoSeedData() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<ProductResponse[]> response = restTemplate.exchange(
                "/api/products?unitId=" + DEMO_UNIT_ID, HttpMethod.GET,
                new HttpEntity<>(headers), ProductResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty(); // produtos do V900__seed_demo_data.sql
    }
}
