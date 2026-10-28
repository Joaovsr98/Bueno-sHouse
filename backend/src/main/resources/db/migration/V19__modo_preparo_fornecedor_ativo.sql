-- RF-014: modo de preparo do prato (texto livre, exibido na pagina de detalhe).
ALTER TABLE products ADD COLUMN preparation_method VARCHAR(1000) NULL AFTER description;

-- Fornecedor pode ser desativado sem apagar o historico de compras e precos.
ALTER TABLE suppliers ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
