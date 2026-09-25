-- Comanda Digital (SRS v3.2) - Fase 3: catalogo por fornecedor (cotacao
-- comparativa) e fluxo completo de pedido de compra (RASCUNHO -> ENVIADO ->
-- RECEBIDO), que antes so existia como uma tabela "purchases" de agregado
-- unico, sem itens de linha nem status.

CREATE TABLE supplier_products (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT UNSIGNED NOT NULL,
    inventory_item_id BIGINT UNSIGNED NOT NULL,
    price DECIMAL(15,4) NOT NULL,
    unit_of_measure VARCHAR(10) NOT NULL,
    CONSTRAINT fk_supplier_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_products_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_supplier_products UNIQUE (supplier_id, inventory_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_supplier_products_item ON supplier_products(inventory_item_id);

-- purchases passa a nascer em RASCUNHO (sem itens/total ainda) e so ganha
-- purchased_at quando confirmado o recebimento (RECEBIDO).
ALTER TABLE purchases ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO';
ALTER TABLE purchases MODIFY total DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE purchases MODIFY purchased_at DATE NULL;

CREATE TABLE purchase_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT UNSIGNED NOT NULL,
    inventory_item_id BIGINT UNSIGNED NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    unit_price DECIMAL(15,4) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
