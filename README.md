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
- **Estoque** — ingredientes, fichas técnicas e **baixa automática de insumos** ao concluir
  cada item na cozinha.
- **Relatórios** — faturamento diário, ticket médio, produtos mais vendidos e estoque baixo.
- **Jornada do cliente** — cardápio digital, carrinho, checkout com endereço e acompanhamento
  do pedido em uma timeline (recebido → em preparo → pronto → a caminho → entregue).
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

- **Frontend:** http://localhost:5173
- **API (health check):** http://localhost:8080/api/health

Para parar tudo: `docker compose down` (adicione `-v` para apagar também os dados do banco).

### Desenvolvimento local (sem Docker)

Requer Java 21, Maven 3.9+, Node.js 20+ e MySQL 8 em execução.

```bash
# Backend
cd backend && mvn spring-boot:run

# Frontend (Angular)
cd frontend-angular && npm install && npm start
```

---

## Testes

Os testes de integração usam Testcontainers e sobem um MySQL descartável automaticamente
(basta ter o Docker disponível):

```bash
cd backend
mvn test
```

Isso valida, contra um MySQL real, que todas as migrations Flyway e os principais fluxos de
negócio funcionam de ponta a ponta.

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
│   │   └── db/migration/        # migrations Flyway (schema + dados de demonstração)
│   ├── src/test/                # testes de integração (Testcontainers)
│   ├── pom.xml
│   └── Dockerfile
├── frontend-angular/            # aplicação Angular (staff + cliente)
│   └── src/app/
│       ├── core/                # auth, guards, interceptors, serviços base
│       └── features/            # telas por domínio (login, mesas, cozinha, caixa, cliente, ...)
├── docker-compose.yml
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

> As credenciais de demonstração existem **apenas no ambiente de desenvolvimento** e não são
> documentadas neste README. Consulte a configuração local de desenvolvimento para
> inicializá-las.

---

## Licença

Projeto de portfólio. Uso e distribuição sob consulta ao autor.
