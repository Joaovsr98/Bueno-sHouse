-- Modulo: organization
-- Restaurantes, unidades, funcionarios e configuracoes.

CREATE TABLE restaurants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    legal_name VARCHAR(150) NOT NULL,
    trade_name VARCHAR(150) NOT NULL,
    document VARCHAR(20) NOT NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_restaurants_document UNIQUE (document)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE units (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    timezone VARCHAR(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_units_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_units_restaurant_id ON units(restaurant_id);

CREATE TABLE employees (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    unit_id BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_employees_user UNIQUE (user_id),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_employees_unit_id ON employees(unit_id);

CREATE TABLE settings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    setting_key VARCHAR(80) NOT NULL,
    setting_value VARCHAR(500),
    CONSTRAINT uq_settings_unit_key UNIQUE (unit_id, setting_key),
    CONSTRAINT fk_settings_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
