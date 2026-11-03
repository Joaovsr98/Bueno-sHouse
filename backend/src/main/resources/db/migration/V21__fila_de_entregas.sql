-- Fila do motoboy. Antes, a entrega nascia junto com o pedido ja como "aguardando
-- entregador": o motoboy via (e podia aceitar) pedido que nem tinha sido confirmado ou
-- que ainda estava na cozinha, e pedido cancelado continuava na lista.
-- Agora a entrega so entra na fila quando o pedido fica pronto (released_at), e a fila
-- anda por ordem de liberacao: primeiro quem esta esperando ha mais tempo.
ALTER TABLE deliveries ADD COLUMN released_at DATETIME(3) NULL;
CREATE INDEX idx_deliveries_status_released ON deliveries (status, released_at);

-- Entregas de pedidos que ja estao prontos: liberadas no momento em que o pedido ficou pronto.
UPDATE deliveries d
JOIN orders o ON o.id = d.order_id
SET d.released_at = COALESCE(
        (SELECT MAX(h.changed_at) FROM order_status_history h
         WHERE h.order_id = o.id AND h.to_status = 'AGUARDANDO_ENTREGADOR'),
        o.created_at)
WHERE o.status = 'AGUARDANDO_ENTREGADOR' AND d.status = 'AGUARDANDO_ENTREGADOR';

-- Pedido cancelado nao tem mais o que entregar (nem a que o motoboy ja tinha pego).
UPDATE deliveries d
JOIN orders o ON o.id = d.order_id
SET d.status = 'CANCELADA'
WHERE o.status = 'CANCELADO' AND d.status IN ('AGUARDANDO_ENTREGADOR', 'ACEITA', 'RETIRADA_NO_RESTAURANTE');

-- Entregas pegas antes de o pedido ficar pronto (a tela deixava): o "Sai para entrega"
-- falhava com "RECEBIDO -> SAIU_PARA_ENTREGA". Voltam para a fila e so reaparecem
-- quando a cozinha terminar o pedido.
UPDATE deliveries d
JOIN orders o ON o.id = d.order_id
SET d.status = 'AGUARDANDO_ENTREGADOR', d.courier_id = NULL, d.accepted_at = NULL, d.picked_up_at = NULL,
    d.released_at = NULL
WHERE d.status IN ('ACEITA', 'RETIRADA_NO_RESTAURANTE')
  AND o.status IN ('RASCUNHO', 'AGUARDANDO_CONFIRMACAO', 'AGUARDANDO_PAGAMENTO', 'PAGAMENTO_CONFIRMADO',
                   'RECEBIDO', 'ENVIADO_PARA_COZINHA', 'EM_PREPARO', 'PARCIALMENTE_PRONTO', 'PRONTO');

-- Motoboy que ficou sem entrega em maos volta a ficar disponivel.
UPDATE couriers c
SET c.status = 'DISPONIVEL'
WHERE c.status = 'OCUPADO'
  AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.courier_id = c.id
                  AND d.status IN ('ACEITA', 'RETIRADA_NO_RESTAURANTE', 'EM_ROTA', 'NAO_ENTREGUE'));
