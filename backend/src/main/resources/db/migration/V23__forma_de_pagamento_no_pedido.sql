-- Forma de pagamento escolhida pelo cliente no site (PIX, CARTAO_CREDITO, CARTAO_DEBITO,
-- DINHEIRO). Antes ia so como texto nas observacoes e o pedido nunca chegava ao caixa;
-- agora, quando a entrega e confirmada (ou a retirada finalizada), o pagamento e lancado.
ALTER TABLE orders ADD COLUMN payment_method VARCHAR(20) NULL;
