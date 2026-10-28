-- Alerta de estoque minimo (RF-032) com o que mostrar na demonstracao (profile dev).
--
-- Depois do V904 o Bacon ficou acima do minimo (1 kg) e o card "Estoque abaixo do
-- minimo" do dashboard ficou vazio. Em vez de mexer no saldo direto, registra uma perda
-- (AJUSTE_PERDA) que leva o Bacon a 0,8 kg: o historico de movimentacoes continua
-- fechando com o saldo. O pedido de compra em RASCUNHO do V904 ja repoe o Bacon.

INSERT INTO stock_movements (inventory_item_id, type, quantity, reason, registered_by)
SELECT i.id, 'AJUSTE_PERDA', i.current_quantity - 0.800,
       'Perda: lote de bacon fora da validade (dados de exemplo)',
       (SELECT id FROM users WHERE email = 'admin@demo.local')
FROM inventory_items i
WHERE i.unit_id = 1 AND i.name = 'Bacon' AND i.current_quantity > 0.800;

UPDATE inventory_items
SET current_quantity = 0.800
WHERE unit_id = 1 AND name = 'Bacon' AND current_quantity > 0.800;
