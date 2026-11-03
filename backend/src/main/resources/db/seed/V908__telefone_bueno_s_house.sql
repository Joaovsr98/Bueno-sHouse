-- Telefone de contato da unidade (exibido em "Onde estamos" no cardapio), no lugar do
-- numero de exemplo do V900.
UPDATE units
SET phone = '(11) 95308-0670'
WHERE id = 1 AND phone = '(11) 90000-0000';
