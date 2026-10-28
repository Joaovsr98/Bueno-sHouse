-- Garante no banco a idempotencia da baixa e do estorno de estoque por item de pedido:
-- no maximo um movimento de cada tipo, por ingrediente, para o mesmo item de pedido.
-- Antes a garantia era so "verifica e depois insere" no codigo (InventoryService), que
-- duas transacoes simultaneas conseguiriam furar. Movimentos manuais (referencia NULL)
-- nao sao afetados: o MySQL permite varios NULL em indice unico.
CREATE UNIQUE INDEX uq_stock_movements_order_item_type_item
    ON stock_movements (reference_order_item_id, type, inventory_item_id);
