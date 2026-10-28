-- RN06 (soft delete): ingrediente desativado some das listagens, mas continua
-- referenciado por fichas tecnicas, compras e movimentacoes antigas.
ALTER TABLE inventory_items ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
