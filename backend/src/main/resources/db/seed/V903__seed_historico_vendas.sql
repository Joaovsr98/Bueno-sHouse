-- Historico de vendas de exemplo (profile dev) para o dashboard e os relatorios terem o que
-- mostrar: ~6 meses de pedidos FINALIZADOS + alguns pedidos em andamento hoje.
--
-- Tudo e gerado em SQL a partir da data do deploy (CURDATE/NOW), sem ids fixos:
--   * 4 a 9 pedidos por dia, +4 nas sextas/sabados/domingos, crescendo ~1/dia a cada mes
--   * horarios entre 11h e 22h; 60% delivery (taxa 5,90), 20% retirada, 20% balcao
--   * itens: um lanche (X-Bacon ou X-Burguer, 1-2 un.), refrigerante em ~70% e pudim em ~25%
-- O marcador notes = 'seed-historico' identifica estas linhas (e permite apaga-las se preciso).

INSERT INTO orders (public_id, order_number, unit_id, channel, created_by, status, subtotal,
                    delivery_fee, total, notes, created_at, completed_at)
WITH RECURSIVE
    days AS (SELECT 0 AS d UNION ALL SELECT d + 1 FROM days WHERE d < 179),
    slots AS (SELECT 1 AS s UNION ALL SELECT s + 1 FROM slots WHERE s < 16),
    base AS (
        SELECT d, s,
               CRC32(CONCAT('pedido-', d, '-', s)) AS h,
               4 + MOD(CRC32(CONCAT('dia-', d)), 6)
                 + CASE WHEN DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL d DAY)) IN (1, 6, 7) THEN 4 ELSE 0 END
                 + FLOOR((179 - d) / 30) AS per_day
        FROM days CROSS JOIN slots
    ),
    timed AS (
        SELECT d, s, h,
               TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL d DAY)) + INTERVAL (660 + MOD(h, 660)) MINUTE AS created
        FROM base
        WHERE s <= per_day
    )
SELECT UUID(),
       100000 + d * 20 + s,
       1,
       CASE WHEN MOD(h, 10) < 6 THEN 'DELIVERY' WHEN MOD(h, 10) < 8 THEN 'RETIRADA' ELSE 'BALCAO' END,
       (SELECT id FROM users WHERE email = 'admin@demo.local'),
       'FINALIZADO',
       0,
       CASE WHEN MOD(h, 10) < 6 THEN 5.90 ELSE 0 END,
       0,
       'seed-historico',
       created,
       created + INTERVAL (25 + MOD(h, 30)) MINUTE
FROM timed
WHERE created + INTERVAL 60 MINUTE < NOW();

-- Lanche principal
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity,
                         subtotal, status, kitchen_sector_id, started_at, completed_at)
SELECT o.id, p.id, p.name, p.base_price, q.qty, p.base_price * q.qty, 'PRONTO', p.kitchen_sector_id,
       o.created_at + INTERVAL 5 MINUTE, o.created_at + INTERVAL 18 MINUTE
FROM orders o
JOIN (SELECT id, 1 + MOD(CRC32(CONCAT('qtd-', order_number)), 2) AS qty,
             CASE WHEN MOD(CRC32(CONCAT('lanche-', order_number)), 100) < 55 THEN 'X-Bacon Duplo'
                  ELSE 'X-Burguer Artesanal' END AS product_name
      FROM orders WHERE notes = 'seed-historico') q ON q.id = o.id
JOIN products p ON p.name = q.product_name AND p.unit_id = 1 AND p.deleted_at IS NULL;

-- Refrigerante (~70% dos pedidos)
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity,
                         subtotal, status, kitchen_sector_id, started_at, completed_at)
SELECT o.id, p.id, p.name, p.base_price, q.qty, p.base_price * q.qty, 'PRONTO', p.kitchen_sector_id,
       o.created_at + INTERVAL 3 MINUTE, o.created_at + INTERVAL 5 MINUTE
FROM orders o
JOIN (SELECT id, 1 + MOD(CRC32(CONCAT('bebida-qtd-', order_number)), 2) AS qty
      FROM orders
      WHERE notes = 'seed-historico' AND MOD(CRC32(CONCAT('bebida-', order_number)), 100) < 70) q ON q.id = o.id
JOIN products p ON p.name = 'Refrigerante Lata 350ml' AND p.unit_id = 1 AND p.deleted_at IS NULL;

-- Pudim (~25% dos pedidos)
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity,
                         subtotal, status, kitchen_sector_id, started_at, completed_at)
SELECT o.id, p.id, p.name, p.base_price, 1, p.base_price, 'PRONTO', p.kitchen_sector_id,
       o.created_at + INTERVAL 4 MINUTE, o.created_at + INTERVAL 8 MINUTE
FROM orders o
JOIN products p ON p.name = 'Pudim de Leite' AND p.unit_id = 1 AND p.deleted_at IS NULL
WHERE o.notes = 'seed-historico' AND MOD(CRC32(CONCAT('sobremesa-', o.order_number)), 100) < 25;

UPDATE orders o
JOIN (SELECT order_id, SUM(subtotal) AS s FROM order_items GROUP BY order_id) x ON x.order_id = o.id
SET o.subtotal = x.s, o.total = x.s + o.delivery_fee
WHERE o.notes = 'seed-historico';

-- Pedidos em andamento hoje (balcao), para os paineis de Pedidos e Cozinha terem movimento.
INSERT INTO orders (public_id, order_number, unit_id, channel, created_by, status, subtotal, total, notes, created_at)
SELECT UUID(), 109000 + x.n, 1, 'BALCAO', (SELECT id FROM users WHERE email = 'admin@demo.local'),
       x.status, 0, 0, 'seed-andamento', NOW() - INTERVAL x.minutes MINUTE
FROM (
    SELECT 1 AS n, 'RECEBIDO' AS status, 4 AS minutes
    UNION ALL SELECT 2, 'RECEBIDO', 9
    UNION ALL SELECT 3, 'ENVIADO_PARA_COZINHA', 14
    UNION ALL SELECT 4, 'ENVIADO_PARA_COZINHA', 19
    UNION ALL SELECT 5, 'EM_PREPARO', 26
) x;

INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity,
                         subtotal, notes, status, kitchen_sector_id, started_at)
SELECT o.id, p.id, p.name, p.base_price, i.qty, p.base_price * i.qty, i.notes,
       CASE o.status WHEN 'RECEBIDO' THEN 'PENDENTE'
                     WHEN 'EM_PREPARO' THEN 'EM_PREPARO'
                     ELSE 'ENVIADO' END,
       p.kitchen_sector_id,
       CASE WHEN o.status = 'EM_PREPARO' THEN NOW() - INTERVAL 10 MINUTE END
FROM orders o
JOIN (
    SELECT 109001 AS num, 'X-Bacon Duplo' AS product, 2 AS qty, 'Um sem cebola' AS notes
    UNION ALL SELECT 109001, 'Refrigerante Lata 350ml', 2, NULL
    UNION ALL SELECT 109002, 'X-Burguer Artesanal', 1, 'Ponto da carne: mal passado'
    UNION ALL SELECT 109003, 'X-Burguer Artesanal', 2, NULL
    UNION ALL SELECT 109003, 'Pudim de Leite', 1, NULL
    UNION ALL SELECT 109004, 'X-Bacon Duplo', 1, 'Bacon extra crocante'
    UNION ALL SELECT 109004, 'Refrigerante Lata 350ml', 1, NULL
    UNION ALL SELECT 109005, 'X-Bacon Duplo', 3, NULL
) i ON i.num = o.order_number
JOIN products p ON p.name = i.product AND p.unit_id = 1 AND p.deleted_at IS NULL
WHERE o.notes = 'seed-andamento';

UPDATE orders o
JOIN (SELECT order_id, SUM(subtotal) AS s FROM order_items GROUP BY order_id) x ON x.order_id = o.id
SET o.subtotal = x.s, o.total = x.s
WHERE o.notes = 'seed-andamento';
