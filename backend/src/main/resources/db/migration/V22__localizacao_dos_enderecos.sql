-- Localizacao dos enderecos (latitude/longitude), buscada pelo servidor no OpenStreetMap
-- logo depois do cadastro. A entrega copia a localizacao junto com o endereco: a tela do
-- motoboy abre com mapa, distancias e "no caminho" na hora, sem buscar de novo no celular.
-- geocoded_at: quando a busca teve resposta (achou ou nao); vazio = ainda vai buscar.
ALTER TABLE customer_addresses
    ADD COLUMN latitude DECIMAL(9,6) NULL,
    ADD COLUMN longitude DECIMAL(9,6) NULL,
    ADD COLUMN geocoded_at DATETIME(3) NULL;

ALTER TABLE deliveries
    ADD COLUMN latitude DECIMAL(9,6) NULL,
    ADD COLUMN longitude DECIMAL(9,6) NULL;
