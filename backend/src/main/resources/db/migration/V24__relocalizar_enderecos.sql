-- A busca de localizacao aceitava resultado de outra cidade quando nao achava nenhum na
-- capital ("Centro" virava o Centro de Guarulhos, a 20 km). Corrigida a regra, as
-- localizacoes ja gravadas sao apagadas e o servidor localiza de novo, aos poucos.
UPDATE customer_addresses SET latitude = NULL, longitude = NULL, geocoded_at = NULL;

UPDATE deliveries SET latitude = NULL, longitude = NULL
WHERE status IN ('AGUARDANDO_ENTREGADOR', 'ACEITA', 'RETIRADA_NO_RESTAURANTE', 'EM_ROTA', 'NAO_ENTREGUE');
