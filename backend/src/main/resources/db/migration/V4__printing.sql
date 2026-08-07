-- Modulo: printing (preparacao minima, sem logica de impressao/fila/driver)

CREATE TABLE printers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(60) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    connection_config VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_printers_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE printer_sectors (
    printer_id BIGINT UNSIGNED NOT NULL,
    kitchen_sector_id BIGINT UNSIGNED NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (printer_id, kitchen_sector_id),
    CONSTRAINT fk_printer_sectors_printer FOREIGN KEY (printer_id) REFERENCES printers(id) ON DELETE CASCADE,
    CONSTRAINT fk_printer_sectors_sector FOREIGN KEY (kitchen_sector_id) REFERENCES kitchen_sectors(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
