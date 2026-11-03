-- Identidade do restaurante de demonstracao (profile dev): o V900 nasceu com nomes
-- genericos ("Sabor Demo", "Rua Exemplo, 123"). Agora o cardapio mostra onde fica a
-- cozinha, com mapa e rota, entao o endereco precisa existir: Rua Pais Leme, em
-- Pinheiros (CEP 05424-150, conferido no ViaCEP), sem numero de proposito, para o mapa
-- nao apontar para um predio de outra empresa.
UPDATE restaurants r
JOIN units u ON u.restaurant_id = r.id AND u.id = 1
SET r.trade_name = 'Bueno''s House'
WHERE r.trade_name = 'Sabor Demo';

UPDATE units
SET name = 'Unidade Pinheiros',
    address = 'Rua Pais Leme - Pinheiros, São Paulo - SP, 05424-150'
WHERE id = 1 AND address = 'Rua Exemplo, 123 - Centro';
