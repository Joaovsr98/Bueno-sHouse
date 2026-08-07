-- Modulo: catalog (+ kitchen_sectors, pre-requisito de products)

CREATE TABLE categories (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at DATETIME(3) NULL,
    CONSTRAINT fk_categories_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_categories_unit_id ON categories(unit_id);

CREATE TABLE kitchen_sectors (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT fk_sectors_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT uq_sectors_unit_name UNIQUE (unit_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    base_price DECIMAL(15,2) NOT NULL,
    image_url VARCHAR(300),
    prep_time_minutes SMALLINT UNSIGNED,
    kitchen_sector_id BIGINT UNSIGNED,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_products_sector FOREIGN KEY (kitchen_sector_id) REFERENCES kitchen_sectors(id) ON DELETE RESTRICT,
    CONSTRAINT chk_products_price CHECK (base_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_products_unit_category ON products(unit_id, category_id);
CREATE INDEX idx_products_available ON products(unit_id, available);

CREATE TABLE product_variations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    price_delta DECIMAL(15,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_variations_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_variations_product_id ON product_variations(product_id);

CREATE TABLE additional_groups (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    min_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    max_quantity INT UNSIGNED NOT NULL DEFAULT 1,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_additional_groups_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT chk_additional_groups_qty CHECK (max_quantity >= min_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE additionals (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    additional_group_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    price DECIMAL(15,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at DATETIME(3) NULL,
    CONSTRAINT fk_additionals_group FOREIGN KEY (additional_group_id) REFERENCES additional_groups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_additionals_group_id ON additionals(additional_group_id);

CREATE TABLE product_additional_groups (
    product_id BIGINT UNSIGNED NOT NULL,
    additional_group_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (product_id, additional_group_id),
    CONSTRAINT fk_pag_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_pag_group FOREIGN KEY (additional_group_id) REFERENCES additional_groups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
