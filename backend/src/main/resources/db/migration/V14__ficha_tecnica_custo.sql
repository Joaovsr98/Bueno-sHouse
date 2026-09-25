-- Comanda Digital (SRS v3.2) - Fase 2: ficha tecnica com fator de correcao e
-- rendimento, necessarios para o calculo de custo do prato e food cost (%).
-- Ambos com DEFAULT 1 para nao quebrar fichas tecnicas ja cadastradas
-- (equivalente a "sem correcao"/"rende 1 unidade").

ALTER TABLE recipe_items ADD COLUMN correction_factor DECIMAL(6,3) NOT NULL DEFAULT 1.000;
ALTER TABLE recipes ADD COLUMN yield_quantity DECIMAL(15,3) NOT NULL DEFAULT 1.000;
