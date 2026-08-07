-- Modulo: ordering

CREATE TABLE orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    order_number BIGINT UNSIGNED NOT NULL,
    unit_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(20) NOT NULL,
    command_id BIGINT UNSIGNED NULL,
    customer_id BIGINT UNSIGNED NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO',
    subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount DECIMAL(15,2) NOT NULL DEFAULT 0,
    service_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    delivery_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    total DECIMAL(15,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_public_id UNIQUE (public_id),
    CONSTRAINT uq_orders_unit_number UNIQUE (unit_id, order_number),
    CONSTRAINT fk_orders_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_command FOREIGN KEY (command_id) REFERENCES commands(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_orders_total CHECK (total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_orders_unit_status ON orders(unit_id, status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);

CREATE TABLE order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    product_name_snapshot VARCHAR(120) NOT NULL,
    unit_price_snapshot DECIMAL(15,2) NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    subtotal DECIMAL(15,2) NOT NULL,
    notes VARCHAR(300),
    variation_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    kitchen_sector_id BIGINT UNSIGNED NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    produced_by BIGINT UNSIGNED NULL,
    cancel_reason VARCHAR(255) NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_variation FOREIGN KEY (variation_id) REFERENCES product_variations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_sector FOREIGN KEY (kitchen_sector_id) REFERENCES kitchen_sectors(id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_produced_by FOREIGN KEY (produced_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_sector_status ON order_items(kitchen_sector_id, status);

CREATE TABLE order_item_additionals (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT UNSIGNED NOT NULL,
    additional_id BIGINT UNSIGNED NOT NULL,
    additional_name_snapshot VARCHAR(80) NOT NULL,
    price_snapshot DECIMAL(15,2) NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    CONSTRAINT fk_oia_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_oia_additional FOREIGN KEY (additional_id) REFERENCES additionals(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_status_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    changed_by BIGINT UNSIGNED NULL,
    changed_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    reason VARCHAR(255) NULL,
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_osh_user FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_osh_order_id ON order_status_history(order_id);
