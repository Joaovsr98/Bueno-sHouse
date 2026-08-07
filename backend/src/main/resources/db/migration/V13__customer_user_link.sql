-- Vincula um Customer (cliente do delivery) a um User com perfil CLIENTE.
-- Nullable: clientes cadastrados pelo balcao/telefone continuam sem conta.
-- UNIQUE: um usuario CLIENTE corresponde a no maximo um cadastro de cliente.
-- Isso permite: (1) o cliente logar e ver/pedir apenas os proprios dados;
-- (2) checagem de propriedade no backend (evita vazamento entre clientes).

ALTER TABLE customers ADD COLUMN user_id BIGINT UNSIGNED NULL;
ALTER TABLE customers ADD CONSTRAINT uq_customers_user UNIQUE (user_id);
ALTER TABLE customers ADD CONSTRAINT fk_customers_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
