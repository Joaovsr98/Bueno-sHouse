-- H4: pedido repetido (clique duplo, rede que reenvia, recarregar no meio) nao vira
-- dois pedidos. O app manda uma chave por tentativa de compra (header Idempotency-Key);
-- a mesma chave do mesmo usuario devolve o pedido ja criado.
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64) NULL;
CREATE UNIQUE INDEX uq_orders_created_by_idempotency_key ON orders (created_by, idempotency_key);
