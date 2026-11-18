-- Dados de exemplo: Hot Dog Completo e Batata Frita, com ingredientes no estoque e ficha tecnica,
-- para o pedido dar baixa certa e o produto ficar "Esgotado" quando faltar ingrediente.
-- Tudo referenciado por nome e protegido por NOT EXISTS (pode rodar em banco ja em uso).

INSERT INTO categories (unit_id, name, display_order)
SELECT 1, 'Porções', 4
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE unit_id = 1 AND name = 'Porções');

INSERT INTO products (unit_id, category_id, name, description, preparation_method, base_price, kitchen_sector_id, available)
SELECT 1, c.id, x.name, x.description, x.preparation_method, x.price, s.id, TRUE
FROM (
    SELECT 'Hot Dog Completo' AS name, 'Lanches' AS category,
           'Pão de hot dog, duas salsichas, molho de tomate, purê de batata, milho, ervilha, vinagrete e batata palha' AS description,
           'Aquecer as salsichas no molho, montar no pão com o purê, milho, ervilha e vinagrete e finalizar com batata palha.' AS preparation_method,
           12.00 AS price
    UNION ALL
    SELECT 'Batata Frita', 'Porções',
           'Porção de 300 g de batata frita crocante, sequinha e salgada na hora',
           'Fritar a batata congelada em óleo a 180 °C por 4 a 5 minutos, escorrer e salgar.',
           18.00
) x
JOIN categories c ON c.unit_id = 1 AND c.name = x.category
JOIN kitchen_sectors s ON s.unit_id = 1 AND s.name = 'CHAPA'
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.unit_id = 1 AND p.name = x.name AND p.deleted_at IS NULL);

INSERT INTO inventory_items (unit_id, name, unit_of_measure, current_quantity, minimum_quantity, cost_per_unit)
SELECT 1, x.name, x.uom, x.qty, x.min_qty, x.cost
FROM (
    SELECT 'Pao de Hot Dog' AS name, 'UN' AS uom, 50.000 AS qty, 20.000 AS min_qty, 0.7000 AS cost
    UNION ALL SELECT 'Salsicha',          'UN', 100.000, 40.000,  0.6500
    UNION ALL SELECT 'Molho de Tomate',   'KG',   5.000,  2.000,  9.0000
    UNION ALL SELECT 'Pure de Batata',    'KG',   4.000,  1.500, 12.0000
    UNION ALL SELECT 'Milho Verde',       'KG',   2.000,  0.800, 14.0000
    UNION ALL SELECT 'Ervilha',           'KG',   2.000,  0.800, 14.0000
    UNION ALL SELECT 'Vinagrete',         'KG',   2.000,  0.800, 10.0000
    UNION ALL SELECT 'Batata Palha',      'KG',   2.000,  0.800, 30.0000
    UNION ALL SELECT 'Batata Congelada',  'KG',  10.000,  4.000, 16.0000
    UNION ALL SELECT 'Oleo de Fritura',   'L',   10.000,  4.000,  8.0000
) x
WHERE NOT EXISTS (SELECT 1 FROM inventory_items i WHERE i.unit_id = 1 AND i.name = x.name);

-- Saldo inicial como entrada, para aparecer no historico de movimentacoes.
INSERT INTO stock_movements (inventory_item_id, type, quantity, reason, registered_by)
SELECT i.id, 'ENTRADA_COMPRA', i.current_quantity, 'Saldo inicial (dados de exemplo)',
       (SELECT id FROM users WHERE email = 'admin@demo.local')
FROM inventory_items i
WHERE i.unit_id = 1
  AND i.name IN ('Pao de Hot Dog', 'Salsicha', 'Molho de Tomate', 'Pure de Batata', 'Milho Verde', 'Ervilha',
                 'Vinagrete', 'Batata Palha', 'Batata Congelada', 'Oleo de Fritura')
  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.inventory_item_id = i.id);

INSERT INTO recipes (product_id, yield_quantity)
SELECT p.id, 1.000
FROM products p
WHERE p.unit_id = 1 AND p.deleted_at IS NULL
  AND p.name IN ('Hot Dog Completo', 'Batata Frita')
  AND NOT EXISTS (SELECT 1 FROM recipes r WHERE r.product_id = p.id);

-- Food cost: Hot Dog ~R$ 4,60 de R$ 12,00 (~38%) | Batata Frita ~R$ 5,40 de R$ 18,00 (~30%).
INSERT INTO recipe_items (recipe_id, inventory_item_id, quantity, correction_factor)
SELECT r.id, i.id, x.quantity, x.correction_factor
FROM (
    SELECT 'Hot Dog Completo' AS product, 'Pao de Hot Dog' AS item, 1.000 AS quantity, 1.000 AS correction_factor
    UNION ALL SELECT 'Hot Dog Completo', 'Salsicha',         2.000, 1.000
    UNION ALL SELECT 'Hot Dog Completo', 'Molho de Tomate',  0.060, 1.000
    UNION ALL SELECT 'Hot Dog Completo', 'Pure de Batata',   0.050, 1.000
    UNION ALL SELECT 'Hot Dog Completo', 'Milho Verde',      0.020, 1.000
    UNION ALL SELECT 'Hot Dog Completo', 'Ervilha',          0.020, 1.000
    UNION ALL SELECT 'Hot Dog Completo', 'Vinagrete',        0.030, 1.100
    UNION ALL SELECT 'Hot Dog Completo', 'Batata Palha',     0.020, 1.000
    UNION ALL SELECT 'Batata Frita',     'Batata Congelada', 0.300, 1.050
    UNION ALL SELECT 'Batata Frita',     'Oleo de Fritura',  0.050, 1.000
) x
JOIN products p ON p.name = x.product AND p.unit_id = 1 AND p.deleted_at IS NULL
JOIN recipes r ON r.product_id = p.id
JOIN inventory_items i ON i.name = x.item AND i.unit_id = 1
WHERE NOT EXISTS (SELECT 1 FROM recipe_items ri WHERE ri.recipe_id = r.id AND ri.inventory_item_id = i.id);
