package com.restaurante.sistema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de fundacao: sobe um MySQL real via Testcontainers, aplica TODAS as
 * migrations Flyway (V1 a V900) e verifica que o contexto Spring inicializa
 * sem erro. Isso valida, na pratica:
 *   - que o DDL da Etapa 3 e sintaticamente valido para o MySQL real;
 *   - que a ordem das migrations respeita as dependencias de FK;
 *   - que os dados de demonstracao (V900) sao inseridos sem violar constraints.
 *
 * Nao testa ainda nenhuma regra de negocio (isso comeca no modulo "identity",
 * na Etapa 5) - e apenas a fundacao tecnica descrita na Etapa 4.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestauranteApplicationIT {

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

    @Test
    void contextLoadsAndAllMigrationsApplySuccessfully() {
        // Se o contexto Spring subiu ate aqui, o Flyway ja aplicou todas as
        // migrations com sucesso (baseline-on-migrate + validate no ddl-auto
        // teriam falhado o boot caso houvesse qualquer inconsistencia).
        assertThat(mysql.isRunning()).isTrue();
    }
}
