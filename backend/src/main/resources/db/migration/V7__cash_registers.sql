-- Modulo: cashregister (parte 1 - abertura/fechamento; cash_movements vem depois de payments)

CREATE TABLE cash_registers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    opened_by BIGINT UNSIGNED NOT NULL,
    closed_by BIGINT UNSIGNED NULL,
    opening_balance DECIMAL(15,2) NOT NULL,
    closing_balance DECIMAL(15,2) NULL,
    expected_balance DECIMAL(15,2) NULL,
    difference DECIMAL(15,2) NULL,
    opened_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    closed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_cash_registers_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_registers_opened_by FOREIGN KEY (opened_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_registers_closed_by FOREIGN KEY (closed_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cash_registers ADD COLUMN open_flag TINYINT
    GENERATED ALWAYS AS (CASE WHEN closed_at IS NULL THEN 1 ELSE NULL END) STORED;
CREATE UNIQUE INDEX uq_cash_registers_unit_open ON cash_registers(unit_id, open_flag);
