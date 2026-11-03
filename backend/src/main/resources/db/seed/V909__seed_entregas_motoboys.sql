-- Entregas de exemplo (profile dev) para o ranking de motoboys do dashboard: mais dois
-- motoboys ficticios (senha admin123, como os outros de demonstracao) e uma entrega concluida
-- para cada pedido de delivery do historico dos ultimos 60 dias, dividida 45/35/20 entre eles.
INSERT INTO users (public_id, email, password_hash, profile_id, active) VALUES
    (UUID(), 'motoboy2@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', (SELECT id FROM profiles WHERE name = 'MOTOBOY'), TRUE),
    (UUID(), 'motoboy3@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', (SELECT id FROM profiles WHERE name = 'MOTOBOY'), TRUE);

INSERT INTO employees (user_id, unit_id, full_name, role)
SELECT id, 1, CASE email WHEN 'motoboy2@demo.local' THEN 'Rafael Lima' ELSE 'Bruna Costa' END, 'MOTOBOY'
FROM users WHERE email IN ('motoboy2@demo.local', 'motoboy3@demo.local');

INSERT INTO couriers (unit_id, user_id, full_name, phone, document, vehicle_type, plate, status)
SELECT 1, id,
       CASE email WHEN 'motoboy2@demo.local' THEN 'Rafael Lima' ELSE 'Bruna Costa' END,
       CASE email WHEN 'motoboy2@demo.local' THEN '(11) 90000-0002' ELSE '(11) 90000-0003' END,
       CASE email WHEN 'motoboy2@demo.local' THEN '00000000002' ELSE '00000000003' END,
       'MOTO',
       CASE email WHEN 'motoboy2@demo.local' THEN 'DEMO234' ELSE 'DEMO345' END,
       'OFFLINE'
FROM users WHERE email IN ('motoboy2@demo.local', 'motoboy3@demo.local');

INSERT INTO deliveries (order_id, courier_id, address_snapshot, neighborhood_snapshot, fee, estimated_minutes,
                        status, confirmation_code, received_by_name, assigned_at, accepted_at, picked_up_at,
                        left_at, delivered_at)
SELECT o.id,
       CASE WHEN MOD(o.order_number, 20) < 9 THEN (SELECT c.id FROM couriers c JOIN users u ON u.id = c.user_id WHERE u.email = 'motoboy@demo.local')
            WHEN MOD(o.order_number, 20) < 16 THEN (SELECT c.id FROM couriers c JOIN users u ON u.id = c.user_id WHERE u.email = 'motoboy2@demo.local')
            ELSE (SELECT c.id FROM couriers c JOIN users u ON u.id = c.user_id WHERE u.email = 'motoboy3@demo.local') END,
       CONCAT('Endereco de exemplo, ', MOD(o.order_number, 900) + 10, ' - ',
              ELT(1 + MOD(o.order_number, 6), 'Pinheiros', 'Vila Madalena', 'Centro', 'Consolação', 'Butantã', 'Itaim Bibi'),
              ', São Paulo/SP - CEP 05400-000'),
       ELT(1 + MOD(o.order_number, 6), 'Pinheiros', 'Vila Madalena', 'Centro', 'Consolação', 'Butantã', 'Itaim Bibi'),
       o.delivery_fee, 35, 'ENTREGUE', '000000', 'Cliente', o.created_at,
       o.created_at + INTERVAL 8 MINUTE, o.created_at + INTERVAL 15 MINUTE, o.created_at + INTERVAL 17 MINUTE,
       o.completed_at
FROM orders o
WHERE o.notes = 'seed-historico' AND o.channel = 'DELIVERY' AND o.status = 'FINALIZADO'
  AND o.created_at >= NOW() - INTERVAL 60 DAY
  AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.order_id = o.id);
