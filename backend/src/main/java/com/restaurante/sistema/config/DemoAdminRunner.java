package com.restaurante.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Login de demonstracao exigido pelo guia da disciplina: admin@email.com / senha123.
 * Fica fora do seed V900 (alterar uma migration ja aplicada quebra o checksum do Flyway)
 * e so existe no profile dev - nunca em producao.
 */
@Component
@Profile("dev")
public class DemoAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAdminRunner.class);
    private static final String EMAIL = "admin@email.com";
    private static final String PASSWORD = "senha123";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoAdminRunner(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, EMAIL);
        Long unitId = jdbc.queryForObject("SELECT MIN(id) FROM units", Long.class);
        if ((existing != null && existing > 0) || unitId == null) {
            return;
        }

        jdbc.update("INSERT INTO users (public_id, email, password_hash, profile_id, active) "
                        + "VALUES (?, ?, ?, (SELECT id FROM profiles WHERE name = 'ADMINISTRADOR'), TRUE)",
                UUID.randomUUID().toString(), EMAIL, passwordEncoder.encode(PASSWORD));
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, EMAIL);
        jdbc.update("INSERT INTO employees (user_id, unit_id, full_name, role) VALUES (?, ?, 'Administrador', 'ADMINISTRADOR')",
                userId, unitId);

        log.info("Usuario de demonstracao '{}' criado.", EMAIL);
    }
}
