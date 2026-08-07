-- Modulo: delivery + couriers

CREATE TABLE delivery_zones (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    zone_name VARCHAR(100) NOT NULL,
    neighborhood VARCHAR(100) NOT NULL,
    fee DECIMAL(15,2) NOT NULL,
    minimum_order_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    estimated_minutes SMALLINT UNSIGNED,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_delivery_zones_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT uq_delivery_zones_unit_neighborhood UNIQUE (unit_id, neighborhood)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE couriers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    document VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(30),
    plate VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    commission_percent DECIMAL(5,2) DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_couriers_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT,
    CONSTRAINT fk_couriers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_couriers_user UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_couriers_unit_status ON couriers(unit_id, status);

CREATE TABLE deliveries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    courier_id BIGINT UNSIGNED NULL,
    delivery_zone_id BIGINT UNSIGNED NULL,
    address_snapshot VARCHAR(500) NOT NULL,
    neighborhood_snapshot VARCHAR(100) NOT NULL,
    reference_point_snapshot VARCHAR(200),
    fee DECIMAL(15,2) NOT NULL,
    estimated_minutes SMALLINT UNSIGNED,
    status VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_ENTREGADOR',
    confirmation_code VARCHAR(10) NOT NULL,
    received_by_name VARCHAR(150) NULL,
    assigned_at DATETIME(3) NULL,
    accepted_at DATETIME(3) NULL,
    picked_up_at DATETIME(3) NULL,
    left_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uq_deliveries_order UNIQUE (order_id),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_deliveries_courier FOREIGN KEY (courier_id) REFERENCES couriers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_deliveries_zone FOREIGN KEY (delivery_zone_id) REFERENCES delivery_zones(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_deliveries_courier_status ON deliveries(courier_id, status);
CREATE INDEX idx_deliveries_status ON deliveries(status);

CREATE TABLE delivery_attempts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    delivery_id BIGINT UNSIGNED NOT NULL,
    attempted_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    outcome VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    next_action VARCHAR(255),
    CONSTRAINT fk_delivery_attempts_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
