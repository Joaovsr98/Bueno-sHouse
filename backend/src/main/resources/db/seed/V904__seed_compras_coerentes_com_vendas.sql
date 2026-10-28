-- Deixa o estoque de exemplo coerente com o historico de vendas do V903 (profile dev).
--
-- O V903 criou ~6 meses de pedidos FINALIZADOS sem baixar o estoque, entao o dashboard
-- mostrava milhares de lanches vendidos com 48 latas e 60 paes no estoque, sem nenhuma
-- compra que explicasse isso. Aqui, para cada mes do historico:
--   * uma compra RECEBIDA (alternando os dois fornecedores) repoe o que as vendas do mes
--     consumiram, pela ficha tecnica (qtd x fator de correcao / rendimento), arredondado
--     para cima; o preco varia por mes e por fornecedor, o que alimenta o historico de
--     precos (RF-026);
--   * a entrada da compra e a baixa das vendas do mes viram movimentacoes de estoque.
-- O saldo final = saldo inicial + compras - consumo, entao continua proximo do V901 (so
-- sobe a sobra do arredondamento) e agora fecha com as vendas.
--
-- No fim, dois pedidos de compra em andamento (RASCUNHO e ENVIADO) para demonstrar o
-- fluxo rascunho -> enviado -> recebido na apresentacao.

CREATE TABLE seed_consumo_tmp (
    mes DATE NOT NULL,
    inventory_item_id BIGINT UNSIGNED NOT NULL,
    consumo DECIMAL(15,3) NOT NULL,
    PRIMARY KEY (mes, inventory_item_id)
);

INSERT INTO seed_consumo_tmp (mes, inventory_item_id, consumo)
SELECT DATE_FORMAT(o.created_at, '%Y-%m-01'), ri.inventory_item_id,
       SUM(oi.quantity * ri.quantity * ri.correction_factor / r.yield_quantity)
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN recipes r ON r.product_id = oi.product_id
JOIN recipe_items ri ON ri.recipe_id = r.id
WHERE o.notes = 'seed-historico'
GROUP BY DATE_FORMAT(o.created_at, '%Y-%m-01'), ri.inventory_item_id;

-- Uma compra recebida por mes, no dia 2 (fornecedor alterna pelo mes).
INSERT INTO purchases (unit_id, supplier_id, total, purchased_at, status)
SELECT 1, s.id, 0, m.mes + INTERVAL 1 DAY, 'RECEBIDO'
FROM (SELECT DISTINCT mes FROM seed_consumo_tmp) m
JOIN suppliers s ON s.unit_id = 1
 AND s.name = CASE WHEN MOD(MONTH(m.mes), 2) = 0 THEN 'Distribuidora Sabor Real' ELSE 'Atacadao Bom Preco' END;

-- Itens: o consumo do mes arredondado para cima; preco sobe ~1,5% ao mes ate hoje,
-- a Distribuidora cobra ~3% menos e cada compra oscila +-2%.
INSERT INTO purchase_items (purchase_id, inventory_item_id, quantity, unit_price, subtotal)
SELECT x.purchase_id, x.inventory_item_id, x.qtd, x.preco, ROUND(x.qtd * x.preco, 2)
FROM (
    SELECT p.id AS purchase_id, c.inventory_item_id, CEIL(c.consumo) AS qtd,
           ROUND(i.cost_per_unit
                 * (1 - 0.015 * TIMESTAMPDIFF(MONTH, c.mes, DATE_FORMAT(CURDATE(), '%Y-%m-01')))
                 * CASE WHEN s.name = 'Distribuidora Sabor Real' THEN 0.97 ELSE 1 END
                 * (1 + (CAST(MOD(CRC32(CONCAT(i.name, c.mes)), 5) AS SIGNED) - 2) / 100), 4) AS preco
    FROM seed_consumo_tmp c
    JOIN purchases p ON p.unit_id = 1 AND p.status = 'RECEBIDO' AND p.purchased_at = c.mes + INTERVAL 1 DAY
    JOIN suppliers s ON s.id = p.supplier_id
    JOIN inventory_items i ON i.id = c.inventory_item_id
) x
WHERE x.qtd > 0;

UPDATE purchases p
JOIN (SELECT purchase_id, SUM(subtotal) AS t FROM purchase_items GROUP BY purchase_id) x ON x.purchase_id = p.id
SET p.total = x.t
WHERE p.unit_id = 1 AND p.status = 'RECEBIDO' AND p.total = 0;

-- Movimentacoes: entrada de cada compra e baixa consolidada das vendas de cada mes.
INSERT INTO stock_movements (inventory_item_id, type, quantity, reason, registered_by, created_at)
SELECT pi.inventory_item_id, 'ENTRADA_COMPRA', pi.quantity,
       CONCAT('Compra #', p.id, ' recebida (dados de exemplo)'),
       (SELECT id FROM users WHERE email = 'admin@demo.local'),
       TIMESTAMP(p.purchased_at, '08:00:00')
FROM purchase_items pi
JOIN purchases p ON p.id = pi.purchase_id
JOIN seed_consumo_tmp c ON c.inventory_item_id = pi.inventory_item_id AND p.purchased_at = c.mes + INTERVAL 1 DAY
WHERE p.unit_id = 1 AND p.status = 'RECEBIDO';

INSERT INTO stock_movements (inventory_item_id, type, quantity, reason, registered_by, created_at)
SELECT c.inventory_item_id, 'SAIDA_PRODUCAO', c.consumo,
       CONCAT('Baixa das vendas de ', DATE_FORMAT(c.mes, '%m/%Y'), ' (dados de exemplo)'),
       (SELECT id FROM users WHERE email = 'admin@demo.local'),
       LEAST(NOW(), TIMESTAMP(LAST_DAY(c.mes), '23:30:00'))
FROM seed_consumo_tmp c;

-- O saldo inicial do V901 passa a ser anterior ao historico, para a linha do tempo fechar.
UPDATE stock_movements sm
JOIN inventory_items i ON i.id = sm.inventory_item_id
SET sm.created_at = (SELECT MIN(mes) FROM seed_consumo_tmp) - INTERVAL 1 DAY
WHERE i.unit_id = 1 AND sm.reason = 'Saldo inicial (dados de exemplo)';

-- Saldo atual = saldo + compras - consumo.
UPDATE inventory_items i
JOIN (
    SELECT c.inventory_item_id, SUM(CEIL(c.consumo) - c.consumo) AS sobra
    FROM seed_consumo_tmp c
    GROUP BY c.inventory_item_id
) x ON x.inventory_item_id = i.id
SET i.current_quantity = i.current_quantity + x.sobra;

DROP TABLE seed_consumo_tmp;

-- Pedidos de compra em andamento para a demonstracao do fluxo.
INSERT INTO purchases (unit_id, supplier_id, total, purchased_at, status)
SELECT 1, id, 0, NULL, 'RASCUNHO' FROM suppliers WHERE unit_id = 1 AND name = 'Atacadao Bom Preco';
SET @rascunho = LAST_INSERT_ID();

INSERT INTO purchase_items (purchase_id, inventory_item_id, quantity, unit_price, subtotal)
SELECT @rascunho, i.id, x.qtd, x.preco, ROUND(x.qtd * x.preco, 2)
FROM (
    SELECT 'Bacon' AS item, 3.000 AS qtd, 45.0000 AS preco
    UNION ALL SELECT 'Refrigerante Lata', 48.000, 1.9000
) x
JOIN inventory_items i ON i.name = x.item AND i.unit_id = 1;

INSERT INTO purchases (unit_id, supplier_id, total, purchased_at, status)
SELECT 1, id, 0, NULL, 'ENVIADO' FROM suppliers WHERE unit_id = 1 AND name = 'Distribuidora Sabor Real';
SET @enviado = LAST_INSERT_ID();

INSERT INTO purchase_items (purchase_id, inventory_item_id, quantity, unit_price, subtotal)
SELECT @enviado, i.id, x.qtd, x.preco, ROUND(x.qtd * x.preco, 2)
FROM (
    SELECT 'Blend Bovino' AS item, 10.000 AS qtd, 35.9000 AS preco
    UNION ALL SELECT 'Cheddar', 2.000, 46.0000
) x
JOIN inventory_items i ON i.name = x.item AND i.unit_id = 1;

UPDATE purchases p
JOIN (SELECT purchase_id, SUM(subtotal) AS t FROM purchase_items GROUP BY purchase_id) x ON x.purchase_id = p.id
SET p.total = x.t
WHERE p.id IN (@rascunho, @enviado);

-- Modo de preparo dos pratos de exemplo (RF-014), exibido na pagina de detalhe do prato.
UPDATE products SET preparation_method = CASE name
    WHEN 'X-Burguer Artesanal' THEN 'Blend de 160 g selado na chapa bem quente, 3 minutos de cada lado. Queijo prato derretido no final, pao brioche tostado na manteiga, alface e tomate frescos.'
    WHEN 'X-Bacon Duplo' THEN 'Dois blends de 120 g na chapa, cheddar derretido por cima de cada um. Bacon em tiras, crocante na propria gordura. Montado no brioche tostado.'
    WHEN 'Refrigerante Lata 350ml' THEN 'Servido gelado, direto da geladeira, com copo e gelo a parte se o cliente pedir.'
    WHEN 'Pudim de Leite' THEN 'Leite condensado, leite e ovos batidos e assados em banho-maria sobre calda de caramelo. Desenformado gelado, em fatias.'
    ELSE preparation_method END
WHERE unit_id = 1 AND deleted_at IS NULL;

-- Fotos dos lanches de exemplo (arquivos do proprio frontend, em /public).
UPDATE products SET image_url = '/comida-cheeseburger.webp'
WHERE unit_id = 1 AND name = 'X-Burguer Artesanal' AND image_url IS NULL;
UPDATE products SET image_url = '/comida-burger-fritas.webp'
WHERE unit_id = 1 AND name = 'X-Bacon Duplo' AND image_url IS NULL;
