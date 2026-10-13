# Deploy em produção (VPS com Docker)

Resultado: o sistema inteiro (Angular + Spring Boot + MySQL) em **uma URL pública com HTTPS**,
atendendo ao entregável de deploy do SRS. Só as portas 80/443 ficam abertas; MySQL e API ficam
na rede interna do Docker.

## Pré-requisitos

- Um servidor Linux (VPS de ~2 GB de RAM basta — Oracle Cloud Free, Hetzner, DigitalOcean, etc.)
  com **Docker + Docker Compose** instalados.
- Um **domínio** (ou subdomínio) com registro DNS tipo `A` apontando para o IP do servidor.
  Sem domínio próprio, serviços gratuitos como DuckDNS servem.
- Portas **80 e 443** liberadas no firewall do provedor.

## Passo a passo

```bash
# 1. No servidor, clone o repositório
git clone https://github.com/Joaovsr98/Bueno-sHouse.git && cd Bueno-sHouse

# 2. Crie o .env a partir do exemplo
cp .env.example .env
```

Edite o `.env` e defina **obrigatoriamente**:

| Variável | Valor |
|---|---|
| `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD` | senhas fortes e diferentes |
| `JWT_SECRET` | string longa e aleatória (`openssl rand -base64 48`) |
| `DOMAIN` | ex.: `comanda.seudominio.com.br` |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | seu primeiro administrador (senha ≥ 8 caracteres) |
| `BOOTSTRAP_RESTAURANT_NAME` | nome do restaurante |

```bash
# 3. Suba (o Caddy emite o certificado HTTPS sozinho na primeira requisição)
docker compose -f docker-compose.prod.yml --env-file .env up -d --build

# 4. Acompanhe o backend até ficar saudável
docker compose -f docker-compose.prod.yml logs -f backend
```

Acesse `https://SEU_DOMINIO`, entre com o administrador do bootstrap e **remova
`BOOTSTRAP_ADMIN_*` do `.env`** (depois `docker compose -f docker-compose.prod.yml up -d`
para recriar o backend). A partir daí crie os demais usuários em **Usuários internos**.

## O que o perfil `prod` faz diferente do `dev`

- **Sem seed de demonstração** (`db/seed` não é carregado) — nada de `admin@demo.local`.
- Apenas as migrations de `db/migration`, que incluem os perfis de acesso (`V16`).
- CORS restrito a `https://DOMAIN`; MySQL/API sem portas publicadas.
- Swagger continua em `/swagger-ui.html` (útil para a banca; remova a rota pública no
  `SecurityConfig` se quiser escondê-lo).

## Atualizar a versão

```bash
git pull && docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## Backup do banco

```bash
docker compose -f docker-compose.prod.yml exec mysql \
  sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > backup.sql
```

## Alternativas

Se preferir PaaS: backend e MySQL no Railway/Render (usar o `backend/Dockerfile` e as mesmas
variáveis do `.env`), e o Angular no Vercel/Netlify/Render Static apontando `/api` para a URL
do backend (o `nginx.conf` desta pasta mostra o proxy esperado; em PaaS é preciso reescrita de
rota `/api/*` → backend e CORS com a origem do front em `CORS_ALLOWED_ORIGINS`).
