# Bueno's House — Sistema de Gestão para Restaurantes

Plataforma de gestão operacional para restaurantes, lanchonetes, hamburguerias e pizzarias.
Cobre a operação de ponta a ponta — **do cardápio à entrega**:

```
cardápio → mesa / atendimento / comanda → pedido → cozinha → pagamento → caixa → delivery → motoboy → confirmação de entrega
```

Construída como um **monólito modular** em Spring Boot, com API REST segura por JWT e um
frontend em Angular que atende tanto a equipe do restaurante (salão, cozinha, caixa,
entregador) quanto o cliente final (cardápio digital, carrinho e acompanhamento do pedido
em tempo real).

---

## Sistema no ar

| | URL |
|---|---|
| **Aplicação (Angular, Vercel)** | https://bueno-s-house.vercel.app |
| **API (Spring Boot, Render)** | https://bueno-s-house-api.onrender.com/api/health |
| **Swagger** | https://bueno-s-house-api.onrender.com/swagger-ui.html |
| **Banco** | MySQL 8 gerenciado (Aiven) |

> O backend roda no plano gratuito do Render e "dorme" após ~15 min sem uso: o primeiro
> acesso pode levar cerca de 1 minuto.

**Logins de demonstração**

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@email.com` | `senha123` |
| Administrador, Gerente, Caixa, Garçom, Cozinha, Motoboy, Cliente | `admin@demo.local`, `gerente@demo.local`, `caixa@demo.local`, `garcom@demo.local`, `cozinha@demo.local`, `motoboy@demo.local`, `cliente@demo.local` | `admin123` |

O cardápio (`/app/cardapio`) é público; clientes novos se cadastram em `/registrar`.

**Arquitetura de produção:** `Angular (Vercel) → /api (rewrite da Vercel) → Spring Boot (Render, Docker) → MySQL (Aiven)`.
Variáveis do backend no Render: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
`SPRING_PROFILES_ACTIVE`, `CORS_ALLOWED_ORIGINS` (ver [render.yaml](render.yaml)). DER do banco: [docs/DER.md](docs/DER.md).

---

## Escopo: guia da disciplina × extensões

| Obrigatório (guia Comanda Digital) | Extensões deste projeto |
|---|---|
| Cardápio público, carrinho, cadastro, checkout, acompanhamento e histórico do cliente | Salão: mesas, atendimentos e comandas |
| CRUD de cardápio, ficha técnica, custo e food cost | Caixa: abertura, fechamento, sangria e suprimento |
| Pedidos e cozinha (RECEBIDO → CONFIRMADO → EM_PREPARO → PRONTO → SAIU_ENTREGA → FINALIZADO) | Delivery com entregador e código de confirmação |
| Baixa de estoque na confirmação e estorno no cancelamento | Multiunidade |
| Fornecedores, cotação e compras | Perfis extras: Caixa, Garçom e Motoboy |
| Controle de estoque e dashboard | Notificações internas e painel da cozinha em tempo real |
| Login JWT e RBAC (Admin, Gerente, Cozinha e Cliente) | |

No código, o status `CONFIRMADO` do guia corresponde a `ENVIADO_PARA_COZINHA` (exibido como
"Confirmado") e o perfil `COZINHEIRO` corresponde a `COZINHA`.

---

## Destaques

- **Multiperfil e multiunidade** — Administrador, Gerente, Caixa, Garçom, Cozinha, Motoboy e
  Cliente, cada um com sua jornada e suas permissões (RBAC por perfil e permissão).
- **Fluxo de salão completo** — mesas, atendimento, comandas e pedidos com máquinas de estado
  validadas; uma mesa nunca tem dois atendimentos ativos ao mesmo tempo.
- **Cozinha em tempo real** — painel de produção com atualização via WebSocket (STOMP)
  autenticado por JWT.
- **Delivery** — zonas de entrega, cálculo automático de taxa por bairro, atribuição de
  entregador com aceite concorrente seguro (optimistic locking) e confirmação por código.
- **Financeiro** — pagamentos, saldo de pedido, abertura/fechamento de caixa, sangria,
  suprimento e conferência; a comanda não fecha com saldo devedor.
- **Estoque** — ingredientes, fichas técnicas e **baixa automática de insumos ao confirmar
  o pedido** (atualização atômica, sem saldo negativo), com estorno no cancelamento e quando
  a cozinha marca um item como indisponível.
- **Relatórios** — vendas diárias (30 dias) e mensais (12 meses), ticket médio, produtos mais
  vendidos, food cost médio e estoque baixo.
- **Jornada do cliente** — cardápio digital, carrinho, checkout com endereço e acompanhamento
  do pedido em uma timeline (recebido → confirmado → em preparo → pronto → saiu para entrega →
  entregue).
- **Segurança por padrão** — senhas com BCrypt, sessão stateless via JWT, bloqueio de conta
  após tentativas incorretas, CORS configurável e preço sempre congelado a partir do catálogo
  (nunca aceito do frontend).

---

## Stack Tecnológica

| Camada | Tecnologias |
|---|---|
| **Backend** | Java 21, Spring Boot 3.3, Spring Data JPA/Hibernate, Spring Security, Flyway, Maven |
| **Banco** | MySQL 8 |
| **Frontend** | Angular 20 (standalone components, signals), TypeScript, Tailwind CSS 4 |
| **Tempo real** | WebSocket + STOMP (autenticado por JWT) |
| **Infraestrutura** | Docker, Docker Compose |
| **Testes** | JUnit 5, Testcontainers (MySQL real em container) |

---

## Arquitetura

Monólito modular organizado por domínio de negócio, com camadas bem definidas
(apresentação → aplicação → domínio ← infraestrutura). Cada módulo é autocontido:

```
identity · organization · catalog · dinein · ordering · kitchen
payments · cashregister · customers · delivery · couriers · inventory
reports · notifications
```

O desacoplamento entre módulos usa **eventos de domínio** (ex.: concluir um item na cozinha
dispara a baixa de estoque; um pedido pronto dispara uma notificação), evitando dependências
diretas entre eles.

---

## Como rodar

### Via Docker (recomendado)

Pré-requisitos: **Docker 24+** e **Docker Compose v2**. Não é necessário ter Java, Node ou
MySQL instalados — tudo roda em containers.

```bash
# 1. Configure as variáveis de ambiente
cp .env.example .env
```

Abra o `.env` e defina valores próprios para `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD` e
`JWT_SECRET` (uma string longa e aleatória — ex.: `openssl rand -base64 48`).

```bash
# 2. Suba a stack completa
docker compose up -d --build

# 3. Acompanhe até o backend ficar saudável
docker compose logs -f backend
```

Acesse:

- **Frontend:** http://localhost:4200
- **API (health check):** http://localhost:8080/api/health

Para parar tudo: `docker compose down` (adicione `-v` para apagar também os dados do banco).

### Desenvolvimento local (sem Docker)

Requer Java 21, Maven 3.9+, Node.js 20+ e MySQL 8 em execução.

```bash
# Backend
cd backend && mvn spring-boot:run

# Frontend (Angular)
cd frontend-angular && npm install && npm start

# Seed de demonstração (admin@demo.local / admin123): rode o backend com SPRING_PROFILES_ACTIVE=dev
```

---

## Testes

Os testes de integração (`*IT`) usam Testcontainers e sobem um MySQL descartável
automaticamente (basta ter o Docker disponível). Eles rodam na fase `verify` (Failsafe):

```bash
cd backend
mvn verify
```

Isso valida, contra um MySQL real e com `ddl-auto=validate`, que todas as migrations Flyway
batem com as entidades e que os fluxos principais funcionam de ponta a ponta: login, catálogo,
salão, pedido → cozinha, pagamento → caixa, delivery completo, baixa e estorno de estoque,
estoque insuficiente (422) e autorização por perfil (401/403/404).

O **GitHub Actions** ([.github/workflows/backend-tests.yml](.github/workflows/backend-tests.yml))
roda a suíte a cada push que mexe no backend.

---

## Estrutura de Pastas

```text
restaurante-sistema/
├── backend/                     # API Spring Boot
│   ├── src/main/java/com/restaurante/sistema/
│   │   ├── config/              # segurança, CORS, WebSocket, cache
│   │   ├── common/              # tratamento global de erros, eventos, health check
│   │   └── modules/             # domínios de negócio (identity, ordering, kitchen, ...)
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/        # migrations Flyway (schema + perfis de acesso) — rodam em todo ambiente
│   │   └── db/seed/             # dados de demonstração — só com SPRING_PROFILES_ACTIVE=dev
│   ├── src/test/                # testes de integração (Testcontainers)
│   ├── pom.xml
│   └── Dockerfile
├── frontend-angular/            # aplicação Angular (staff + cliente)
│   └── src/app/
│       ├── core/                # auth, guards, interceptors, serviços base
│       └── features/            # telas por domínio (login, mesas, cozinha, caixa, cliente, ...)
├── docker-compose.yml           # ambiente local
├── docker-compose.prod.yml      # produção (HTTPS via Caddy) — ver docs/DEPLOY.md
├── .env.example
└── README.md
```

---

## Segurança

- **Autenticação:** JWT (Bearer) stateless. O token é validado tanto na API REST quanto no
  handshake do WebSocket (`/ws`), que recusa conexões sem token válido.
- **Autorização:** RBAC por perfil e permissão, aplicada por endpoint (`@PreAuthorize`).
- **Senhas:** BCrypt com salt por usuário; bloqueio temporário de conta após tentativas
  incorretas.
- **Configuração sensível:** nunca versionada. O repositório traz apenas `.env.example`;
  segredos reais ficam no `.env` local (ignorado pelo Git).

---

## Dados de demonstração

Em ambiente de desenvolvimento, a carga inicial cria automaticamente um restaurante e uma
unidade de exemplo, os perfis de acesso, usuários de demonstração (um por perfil), além de
categorias, produtos, mesas e zonas de entrega.

> As credenciais de demonstração (tabela em **Sistema no ar**) existem apenas com o perfil
> `dev`. Em produção real use `SPRING_PROFILES_ACTIVE=prod` e o `BOOTSTRAP_ADMIN_*` (ver [docs/DEPLOY.md](docs/DEPLOY.md)).

---

## Licença

Projeto de portfólio. Uso e distribuição sob consulta ao autor.

---

## API docs, paginação e usuários internos

- **Swagger UI:** `http://localhost:8080/swagger-ui.html` (JSON em `/v3/api-docs`).
- **Paginação de pedidos:** `GET /api/orders?unitId=1&page=0&size=20` devolve a página e o total
  no header `X-Total-Count`. Sem `page`, devolve a lista completa (comportamento anterior).
- **Usuários internos (só ADMINISTRADOR):** `GET/POST /api/users`, `PATCH /api/users/{id}`
  (troca de perfil, ativar/desativar, redefinir senha). Perfil `CLIENTE` só via cadastro público.
- **Login de demonstração (dev):** `admin@email.com` / `senha123` ou `admin@demo.local` / `admin123` (nunca usar em produção).
