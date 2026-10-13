package com.restaurante.sistema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cria o PRIMEIRO administrador (e restaurante/unidade) em bancos novos, tipicamente em producao,
 * onde nao ha seed de demonstracao. So age quando:
 *  - BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD estao definidos, E
 *  - ainda nao existe nenhum usuario ADMINISTRADOR.
 * Depois do primeiro boot as variaveis podem (e devem) ser removidas.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String restaurantName;

    public BootstrapAdminRunner(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-email:}") String email,
            @Value("${app.bootstrap.admin-password:}") String password,
            @Value("${app.bootstrap.restaurant-name:Meu Restaurante}") String restaurantName
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.restaurantName = restaurantName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        Integer admins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u JOIN profiles p ON p.id = u.profile_id WHERE p.name = 'ADMINISTRADOR'",
                Integer.class);
        if (admins != null && admins > 0) {
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD deve ter ao menos 8 caracteres");
        }

        Integer units = jdbc.queryForObject("SELECT COUNT(*) FROM units", Integer.class);
        Long unitId;
        if (units == null || units == 0) {
            jdbc.update("INSERT INTO restaurants (legal_name, trade_name, document) VALUES (?, ?, ?)",
                    restaurantName, restaurantName, "00000000000000");
            Long restaurantId = jdbc.queryForObject("SELECT MAX(id) FROM restaurants", Long.class);
            jdbc.update("INSERT INTO units (restaurant_id, name) VALUES (?, ?)", restaurantId, "Unidade Principal");
        }
        unitId = jdbc.queryForObject("SELECT MIN(id) FROM units", Long.class);

        jdbc.update("INSERT INTO users (public_id, email, password_hash, profile_id, active) "
                        + "VALUES (?, ?, ?, (SELECT id FROM profiles WHERE name = 'ADMINISTRADOR'), TRUE)",
                UUID.randomUUID().toString(), email.trim().toLowerCase(), passwordEncoder.encode(password));
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email.trim().toLowerCase());
        jdbc.update("INSERT INTO employees (user_id, unit_id, full_name, role) VALUES (?, ?, 'Administrador', 'ADMINISTRADOR')",
                userId, unitId);

        log.warn("Administrador inicial '{}' criado. Remova BOOTSTRAP_ADMIN_* do ambiente.", email);
    }
}
