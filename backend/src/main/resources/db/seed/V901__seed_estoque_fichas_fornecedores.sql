-- Dados de exemplo (guia, secao 12: "seed obrigatorio + dados de exemplo"), so no profile dev.
-- Ingredientes com saldo, fichas tecnicas dos 4 pratos do V900 e 2 fornecedores com
-- catalogo para a cotacao comparativa. Tudo referenciado por nome (nunca por id fixo),
-- porque bancos ja em uso podem ter outros registros.
--
-- Food cost resultante (custo = SUM(qtd x fator x custo_unitario) / rendimento):
--   X-Burguer Artesanal ~33% (amarelo) | X-Bacon Duplo ~44% (vermelho, aviso RN02)
--   Refrigerante ~27% (verde)          | Pudim de Leite ~11% (verde)
-- O Bacon nasce abaixo do minimo para o alerta de estoque (RF-032) ter o que mostrar.

INSERT INTO inventory_items (unit_id, name, unit_of_measure, current_quantity, minimum_quantity, cost_per_unit) VALUES
    (1, 'Pao Brioche',            'UN', 60.000, 20.000,  1.2000),
    (1, 'Blend Bovino',           'KG',  8.000,  3.000, 38.0000),
    (1, 'Queijo Prato',           'KG',  3.000,  1.000, 42.0000),
    (1, 'Bacon',                  'KG',  0.800,  1.000, 45.0000),
    (1, 'Cheddar',                'KG',  2.000,  1.000, 48.0000),
    (1, 'Alface',                 'KG',  1.500,  0.500,  8.0000),
    (1, 'Tomate',                 'KG',  3.000,  1.000,  7.0000),
    (1, 'Refrigerante Lata',      'UN', 48.000, 24.000,  1.9000),
    (1, 'Leite Condensado',       'UN', 10.000,  4.000,  6.5000),
    (1, 'Leite Integral',         'L',   6.000,  3.000,  5.0000),
    (1, 'Ovo',                    'UN', 30.000, 12.000,  0.8000);

-- Saldo inicial registrado como entrada, para aparecer no historico de movimentacoes (RF-033).
INSERT INTO stock_movements (inventory_item_id, type, quantity, reason, registered_by)
SELECT i.id, 'ENTRADA_COMPRA', i.current_quantity, 'Saldo inicial (dados de exemplo)',
       (SELECT id FROM users WHERE email = 'admin@demo.local')
FROM inventory_items i
WHERE i.unit_id = 1 AND i.name IN ('Pao Brioche', 'Blend Bovino', 'Queijo Prato', 'Bacon', 'Cheddar', 'Alface',
                                    'Tomate', 'Refrigerante Lata', 'Leite Condensado', 'Leite Integral', 'Ovo');

-- Fichas tecnicas (uma por prato, so se o prato ainda nao tiver).
INSERT INTO recipes (product_id, yield_quantity)
SELECT p.id, CASE WHEN p.name = 'Pudim de Leite' THEN 8.000 ELSE 1.000 END
FROM products p
WHERE p.unit_id = 1 AND p.deleted_at IS NULL
  AND p.name IN ('X-Burguer Artesanal', 'X-Bacon Duplo', 'Refrigerante Lata 350ml', 'Pudim de Leite')
  AND NOT EXISTS (SELECT 1 FROM recipes r WHERE r.product_id = p.id);

INSERT INTO recipe_items (recipe_id, inventory_item_id, quantity, correction_factor)
SELECT r.id, i.id, x.quantity, x.correction_factor
FROM (
    SELECT 'X-Burguer Artesanal' AS product, 'Pao Brioche' AS item, 1.000 AS quantity, 1.000 AS correction_factor
    UNION ALL SELECT 'X-Burguer Artesanal', 'Blend Bovino', 0.160, 1.100
    UNION ALL SELECT 'X-Burguer Artesanal', 'Queijo Prato', 0.030, 1.000
    UNION ALL SELECT 'X-Burguer Artesanal', 'Alface', 0.020, 1.250
    UNION ALL SELECT 'X-Burguer Artesanal', 'Tomate', 0.030, 1.100
    UNION ALL SELECT 'X-Bacon Duplo', 'Pao Brioche', 1.000, 1.000
    UNION ALL SELECT 'X-Bacon Duplo', 'Blend Bovino', 0.240, 1.100
    UNION ALL SELECT 'X-Bacon Duplo', 'Bacon', 0.050, 1.000
    UNION ALL SELECT 'X-Bacon Duplo', 'Cheddar', 0.040, 1.000
    UNION ALL SELECT 'Refrigerante Lata 350ml', 'Refrigerante Lata', 1.000, 1.000
    UNION ALL SELECT 'Pudim de Leite', 'Leite Condensado', 1.000, 1.000
    UNION ALL SELECT 'Pudim de Leite', 'Leite Integral', 0.400, 1.000
    UNION ALL SELECT 'Pudim de Leite', 'Ovo', 3.000, 1.000
) x
JOIN products p ON p.name = x.product AND p.unit_id = 1 AND p.deleted_at IS NULL
JOIN recipes r ON r.product_id = p.id
JOIN inventory_items i ON i.name = x.item AND i.unit_id = 1
WHERE NOT EXISTS (SELECT 1 FROM recipe_items ri WHERE ri.recipe_id = r.id AND ri.inventory_item_id = i.id);

-- Fornecedores (CNPJs com digitos verificadores validos, RN07) e catalogo para a cotacao (RF-022/023).
INSERT INTO suppliers (unit_id, name, phone, document) VALUES
    (1, 'Atacadao Bom Preco', '(11) 3000-1000', '11.222.333/0001-81'),
    (1, 'Distribuidora Sabor Real', '(11) 3000-2000', '45.287.165/0001-24');

INSERT INTO supplier_products (supplier_id, inventory_item_id, price, unit_of_measure)
SELECT s.id, i.id, x.price, i.unit_of_measure
FROM (
    SELECT 'Atacadao Bom Preco' AS supplier, 'Blend Bovino' AS item, 38.0000 AS price
    UNION ALL SELECT 'Distribuidora Sabor Real', 'Blend Bovino', 35.9000
    UNION ALL SELECT 'Atacadao Bom Preco', 'Bacon', 45.0000
    UNION ALL SELECT 'Distribuidora Sabor Real', 'Bacon', 47.5000
    UNION ALL SELECT 'Atacadao Bom Preco', 'Pao Brioche', 1.2000
    UNION ALL SELECT 'Distribuidora Sabor Real', 'Pao Brioche', 1.3500
    UNION ALL SELECT 'Atacadao Bom Preco', 'Queijo Prato', 42.0000
    UNION ALL SELECT 'Distribuidora Sabor Real', 'Cheddar', 46.0000
) x
JOIN suppliers s ON s.name = x.supplier AND s.unit_id = 1
JOIN inventory_items i ON i.name = x.item AND i.unit_id = 1;
