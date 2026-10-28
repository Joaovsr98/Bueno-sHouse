-- Zonas de entrega de exemplo com bairros reais de Sao Paulo (profile dev), no lugar do
-- "Jardim Exemplo" ficticio. zone_name guarda a regiao (o cadastro e o checkout agrupam
-- os bairros por ela); a taxa e o tempo crescem com a distancia da cozinha (Zona Oeste).
-- "Centro" ja existe no V900 e continua valendo (endereco do cliente de demonstracao).

UPDATE delivery_zones SET active = FALSE WHERE unit_id = 1 AND neighborhood = 'Jardim Exemplo';
UPDATE delivery_zones SET zone_name = 'Centro', fee = 5.90, minimum_order_value = 25.00, estimated_minutes = 35
WHERE unit_id = 1 AND neighborhood = 'Centro';

INSERT INTO delivery_zones (unit_id, zone_name, neighborhood, fee, minimum_order_value, estimated_minutes)
SELECT 1, z.zone_name, z.neighborhood, z.fee, z.minimum_order_value, z.estimated_minutes
FROM (
    SELECT 'Zona Oeste' AS zone_name, 'Pinheiros' AS neighborhood, 4.90 AS fee, 25.00 AS minimum_order_value, 25 AS estimated_minutes
    UNION ALL SELECT 'Zona Oeste', 'Vila Madalena', 4.90, 25.00, 25
    UNION ALL SELECT 'Zona Oeste', 'Perdizes', 5.90, 25.00, 30
    UNION ALL SELECT 'Zona Oeste', 'Lapa', 6.90, 30.00, 35
    UNION ALL SELECT 'Zona Oeste', 'Butantã', 6.90, 30.00, 35
    UNION ALL SELECT 'Centro', 'Bela Vista', 5.90, 25.00, 35
    UNION ALL SELECT 'Centro', 'Consolação', 5.90, 25.00, 30
    UNION ALL SELECT 'Centro', 'Liberdade', 6.90, 25.00, 40
    UNION ALL SELECT 'Centro', 'República', 6.90, 25.00, 40
    UNION ALL SELECT 'Zona Sul', 'Jardins', 5.90, 25.00, 30
    UNION ALL SELECT 'Zona Sul', 'Itaim Bibi', 6.90, 30.00, 35
    UNION ALL SELECT 'Zona Sul', 'Vila Mariana', 7.90, 30.00, 40
    UNION ALL SELECT 'Zona Sul', 'Moema', 7.90, 30.00, 40
    UNION ALL SELECT 'Zona Sul', 'Brooklin', 8.90, 35.00, 45
    UNION ALL SELECT 'Zona Sul', 'Saúde', 8.90, 35.00, 45
    UNION ALL SELECT 'Zona Norte', 'Santana', 9.90, 40.00, 50
    UNION ALL SELECT 'Zona Norte', 'Casa Verde', 9.90, 40.00, 50
    UNION ALL SELECT 'Zona Leste', 'Mooca', 9.90, 40.00, 50
    UNION ALL SELECT 'Zona Leste', 'Tatuapé', 10.90, 40.00, 55
) z
WHERE NOT EXISTS (
    SELECT 1 FROM delivery_zones d WHERE d.unit_id = 1 AND d.neighborhood = z.neighborhood
);
