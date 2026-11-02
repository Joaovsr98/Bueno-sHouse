-- Remove os enderecos de teste criados por engano no ambiente publicado (02/11/2026) ao
-- conferir a validacao de CEP: rua "x", numero "1" e CEP "abc", sem apelido. Antes da
-- validacao o backend aceitava esse CEP. Nenhuma tabela referencia customer_addresses
-- (pedidos guardam o endereco como texto), entao a remocao nao afeta pedidos.
DELETE FROM customer_addresses
WHERE street = 'x' AND number = '1' AND zip_code = 'abc' AND label IS NULL;
