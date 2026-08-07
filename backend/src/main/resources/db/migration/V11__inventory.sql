-- Modulo: inventory (modelado conforme Etapa 2/3 - sem automacao de baixa)

CREATE TABLE suppliers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    document VARCHAR(20),
    CONSTRAINT fk_suppliers_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    unit_of_measure VARCHAR(10) NOT NULL,
    current_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    minimum_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    cost_per_unit DECIMAL(15,4) DEFAULT 0,
    CONSTRAINT fk_inventory_items_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_inventory_items_unit_id ON inventory_items(unit_id);

CREATE TABLE recipes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT fk_recipes_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_recipes_product UNIQUE (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE recipe_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT UNSIGNED NOT NULL,
    inventory_item_id BIGINT UNSIGNED NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    CONSTRAINT fk_recipe_items_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_items_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_movements (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    reference_order_item_id BIGINT UNSIGNED NULL,
    reason VARCHAR(255),
    registered_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_order_item FOREIGN KEY (reference_order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (registered_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchases (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    supplier_id BIGINT UNSIGNED NOT NULL,
    total DECIMAL(15,2) NOT NULL,
    purchased_at DATE NOT NULL,
    CONSTRAINT fk_purchases_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
