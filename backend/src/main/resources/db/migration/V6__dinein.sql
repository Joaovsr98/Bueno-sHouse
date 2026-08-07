-- Modulo: dinein (Table -> Service -> Command)

CREATE TABLE tables (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    number VARCHAR(10) NOT NULL,
    capacity SMALLINT UNSIGNED NOT NULL DEFAULT 4,
    status VARCHAR(20) NOT NULL DEFAULT 'LIVRE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uq_tables_unit_number UNIQUE (unit_id, number),
    CONSTRAINT fk_tables_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tables_unit_status ON tables(unit_id, status);

-- Service (Atendimento): preserva o historico de ocupacao da mesa
CREATE TABLE services (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    table_id BIGINT UNSIGNED NOT NULL,
    opened_by BIGINT UNSIGNED NOT NULL,
    party_size SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes VARCHAR(300),
    cancel_reason VARCHAR(255) NULL,
    opened_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    closed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_services_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_services_table FOREIGN KEY (table_id) REFERENCES tables(id) ON DELETE RESTRICT,
    CONSTRAINT fk_services_opened_by FOREIGN KEY (opened_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_services_status CHECK (status IN ('OPEN','IN_SERVICE','AWAITING_CLOSURE','CLOSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_services_table_status ON services(table_id, status);

-- Coluna gerada: garante no maximo um Service ativo por mesa
ALTER TABLE services ADD COLUMN active_flag TINYINT
    GENERATED ALWAYS AS (CASE WHEN status IN ('OPEN','IN_SERVICE','AWAITING_CLOSURE') THEN 1 ELSE NULL END) STORED;
CREATE UNIQUE INDEX uq_services_table_active ON services(table_id, active_flag);

-- Command agora pertence ao Service, nao mais diretamente a Table
CREATE TABLE commands (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT UNSIGNED NOT NULL,
    opened_by BIGINT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ABERTA',
    opened_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    closed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_commands_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT,
    CONSTRAINT fk_commands_opened_by FOREIGN KEY (opened_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_commands_status CHECK (status IN
        ('ABERTA','EM_ATENDIMENTO','AGUARDANDO_PAGAMENTO','PARCIALMENTE_PAGA','FECHADA','CANCELADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_commands_service_status ON commands(service_id, status);
