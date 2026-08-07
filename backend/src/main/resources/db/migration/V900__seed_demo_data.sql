-- ATENCAO: esta migration contem dados de DEMONSTRACAO/DESENVOLVIMENTO.
-- Ela roda no ambiente local via Flyway normalmente (Flyway nao possui
-- filtro nativo por profile), mas foi numerada na faixa 900+ para deixar
-- explicito que e "dado de exemplo", nao estrutura.
--
-- Antes de publicar em producao: NAO aplique esta migration no banco de
-- producao. Uma estrategia recomendada para a Etapa 5+ e mover este arquivo
-- para uma pasta separada (ex.: classpath:db/seed) habilitada apenas quando
-- SPRING_PROFILES_ACTIVE=dev, via configuracao adicional do Flyway por
-- profile no application-dev.yml. Por ora, ela e comentada/documentada aqui
-- e devera ser revisada antes do primeiro deploy real.
--
-- Senha do usuario admin de desenvolvimento: "admin123" (hash BCrypt abaixo).
-- NUNCA usar esta senha ou este usuario em producao.

INSERT INTO restaurants (legal_name, trade_name, document)
VALUES ('Restaurante Demonstracao LTDA', 'Sabor Demo', '00000000000100');

INSERT INTO units (restaurant_id, name, address, phone, timezone)
VALUES (1, 'Unidade Centro', 'Rua Exemplo, 123 - Centro', '(11) 90000-0000', 'America/Sao_Paulo');

INSERT INTO profiles (name, description) VALUES
    ('ADMINISTRADOR', 'Acesso total ao sistema'),
    ('GERENTE', 'Gestao operacional da unidade'),
    ('CAIXA', 'Operacao de caixa e pagamentos'),
    ('GARCOM', 'Atendimento de mesas e comandas'),
    ('COZINHA', 'Painel de producao'),
    ('MOTOBOY', 'Entregas'),
    ('CLIENTE', 'Cliente do delivery (app do cliente)');

-- Senha "admin123" com BCrypt real (hash gerado e verificado nesta etapa) -
-- reaproveitada para todos os usuarios de demonstracao abaixo, um por perfil,
-- para permitir login com 1 clique na tela de login (botoes de acesso rapido).
-- NUNCA usar estes usuarios/senha em producao - apenas ambiente de desenvolvimento.
INSERT INTO users (public_id, email, password_hash, profile_id, active) VALUES
    (UUID(), 'admin@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 1, TRUE),
    (UUID(), 'gerente@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 2, TRUE),
    (UUID(), 'caixa@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 3, TRUE),
    (UUID(), 'garcom@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 4, TRUE),
    (UUID(), 'cozinha@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 5, TRUE),
    (UUID(), 'motoboy@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 6, TRUE),
    (UUID(), 'cliente@demo.local', '$2a$10$qDtse0v5jBU576Bfo4/LNOzXRwL4GhO4/JOBxW4YoXLAgsa/QCS4C', 7, TRUE);

INSERT INTO employees (user_id, unit_id, full_name, role) VALUES
    (1, 1, 'Administrador Demo', 'ADMINISTRADOR'),
    (2, 1, 'Gerente Demo', 'GERENTE'),
    (3, 1, 'Caixa Demo', 'CAIXA'),
    (4, 1, 'Garcom Demo', 'GARCOM'),
    (5, 1, 'Cozinha Demo', 'COZINHA'),
    (6, 1, 'Motoboy Demo', 'MOTOBOY');

INSERT INTO couriers (unit_id, user_id, full_name, phone, document, vehicle_type, plate, status)
VALUES (1, 6, 'Motoboy Demo', '(11) 90000-0001', '00000000000', 'MOTO', 'DEMO123', 'DISPONIVEL');

INSERT INTO categories (unit_id, name, display_order) VALUES
    (1, 'Lanches', 1),
    (1, 'Bebidas', 2),
    (1, 'Sobremesas', 3);

INSERT INTO kitchen_sectors (unit_id, name) VALUES
    (1, 'CHAPA'),
    (1, 'BEBIDAS'),
    (1, 'SOBREMESAS'),
    (1, 'EXPEDICAO');

INSERT INTO products (unit_id, category_id, name, description, base_price, kitchen_sector_id, available) VALUES
    (1, 1, 'X-Burguer Artesanal', 'Pao brioche, blend 160g, queijo prato, alface e tomate', 28.90, 1, TRUE),
    (1, 1, 'X-Bacon Duplo', 'Dois blends de 120g, bacon crocante e cheddar', 34.90, 1, TRUE),
    (1, 2, 'Refrigerante Lata 350ml', 'Coca-Cola, Guarana ou Fanta', 7.00, 2, TRUE),
    (1, 3, 'Pudim de Leite', 'Fatia individual', 12.00, 3, TRUE);

INSERT INTO tables (unit_id, number, capacity) VALUES
    (1, '01', 2),
    (1, '02', 4),
    (1, '03', 4),
    (1, '04', 6),
    (1, '05', 2);

INSERT INTO delivery_zones (unit_id, zone_name, neighborhood, fee, minimum_order_value, estimated_minutes) VALUES
    (1, 'Centro', 'Centro', 5.00, 20.00, 30),
    (1, 'Zona Sul Proxima', 'Jardim Exemplo', 8.00, 25.00, 40);

-- Cliente de demonstracao vinculado ao usuario cliente@demo.local (user_id 7),
-- com um endereco padrao no bairro Centro (que tem zona de entrega).
INSERT INTO customers (user_id, full_name, phone, email)
VALUES (7, 'Cliente Demo', '(11) 90000-9999', 'cliente@demo.local');

INSERT INTO customer_addresses (customer_id, label, street, number, neighborhood, city, state, zip_code, is_default)
VALUES (1, 'Casa', 'Rua das Flores', '100', 'Centro', 'Sao Paulo', 'SP', '01000-000', TRUE);
