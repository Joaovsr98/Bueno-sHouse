-- Dados de referencia OBRIGATORIOS (rodam em qualquer ambiente, inclusive producao):
-- perfis de acesso. O AuthService depende de CLIENTE existir para o auto-cadastro.
-- INSERT IGNORE: idempotente em bancos que ja receberam esses perfis pelo antigo seed.
INSERT IGNORE INTO profiles (name, description) VALUES
    ('ADMINISTRADOR', 'Acesso total ao sistema'),
    ('GERENTE', 'Gestao operacional da unidade'),
    ('CAIXA', 'Operacao de caixa e pagamentos'),
    ('GARCOM', 'Atendimento de mesas e comandas'),
    ('COZINHA', 'Painel de producao'),
    ('MOTOBOY', 'Entregas'),
    ('CLIENTE', 'Cliente do delivery (app do cliente)');
