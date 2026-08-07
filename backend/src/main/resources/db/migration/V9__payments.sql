-- Modulo: payments (+ cash_movements, que depende de cash_registers ja criado em V7)

CREATE TABLE payments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    registered_by BIGINT UNSIGNED NOT NULL,
    cash_register_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_user FOREIGN KEY (registered_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_cash_register FOREIGN KEY (cash_register_id) REFERENCES cash_registers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payments_order_id ON payments(order_id);

CREATE TABLE cash_movements (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cash_register_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    reason VARCHAR(255),
    registered_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_cash_movements_register FOREIGN KEY (cash_register_id) REFERENCES cash_registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_movements_user FOREIGN KEY (registered_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_cash_movements_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cash_movements_register_id ON cash_movements(cash_register_id);
