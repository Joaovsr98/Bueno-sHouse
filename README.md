# Sistema de Gestão para Restaurante

Sistema de gestão operacional para restaurantes, lanchonetes, hamburguerias e pizzarias.
Cobre o fluxo completo: cardápio → mesa/atendimento/comanda → pedido → cozinha → pagamento
→ caixa → delivery → motoboy → confirmação de entrega.

> **Status atual do projeto: backend completo (todos os módulos do escopo original implementados). Frontend em migração de React para Angular — telas de staff e a jornada do cliente já portadas.**
> Veja `CONTINUAR-PROJETO.md` para saber exatamente o que já existe, o que foi testado
> e quais são os próximos passos.

---

## Stack Tecnológica

**Backend:** Java 21, Spring Boot 3.3, Spring Data JPA/Hibernate, Spring Security, MySQL 8,
Flyway, Maven.

**Frontend:** Angular 20 (standalone components, signals), TypeScript, Tailwind CSS 4.
O frontend React original (`frontend/`) é mantido como referência até a paridade ser
confirmada, mas o desenvolvimento ativo é no Angular (`frontend-angular/`).

**Infraestrutura:** Docker, Docker Compose.

**Testes:** JUnit 5, Testcontainers (MySQL real em container para os testes de integração).

---

## Pré-requisitos (para rodar via Docker — recomendado)

- Docker 24+
- Docker Compose v2 (`docker compose`, não `docker-compose`)

Você **não precisa** ter Java, Node ou MySQL instalados na máquina para rodar via Docker —
tudo roda dentro dos containers.

### Pré-requisitos para desenvolvimento local sem Docker (opcional)

- Java 21 (JDK)
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+ rodando localmente

---

## Como rodar (passo a passo)

1. Extraia o `.zip` em uma pasta de sua preferência.
2. Copie o arquivo de variáveis de ambiente de exemplo:

   ```bash
   cp .env.example .env
   ```

3. Abra o `.env` e **troque as senhas de exemplo** por valores reais (mesmo em ambiente
   local, evite deixar senhas óbvias). Campos que você precisa preencher:
   - `MYSQL_ROOT_PASSWORD`
   - `DB_PASSWORD`
   - `JWT_SECRET` (uma string longa e aleatória — pode gerar com `openssl rand -base64 48`)

4. Suba tudo com Docker Compose:

   ```bash
   docker compose up -d --build
   ```

   Na primeira vez isso vai demorar alguns minutos (build do Maven e do npm).

5. Acompanhe os logs até o backend ficar saudável:

   ```bash
   docker compose logs -f backend
   ```

   Você deve ver o Flyway aplicando as migrations `V1` a `V900` e, em seguida,
   `Started RestauranteApplication`.

6. Verifique se está tudo no ar:
   - Backend (health check): http://localhost:8080/api/health
   - Frontend: http://localhost:5173

7. Para parar tudo:

   ```bash
   docker compose down
   ```

   Para parar e **apagar também os dados do banco** (recomeçar do zero):

   ```bash
   docker compose down -v
   ```

---

## Rodando os testes do backend

Os testes de integração usam Testcontainers, que precisa do Docker disponível mesmo
rodando os testes fora de container (ele sobe um MySQL descartável automaticamente):

```bash
cd backend
mvn test
```

Isso valida que todas as migrations Flyway (estrutura + dados de demonstração) aplicam
corretamente contra um MySQL real.

---

## Estrutura de Pastas

```text
restaurante-sistema/
├── backend/
│   ├── src/main/java/com/restaurante/sistema/
│   │   ├── RestauranteApplication.java
│   │   ├── config/              (segurança, CORS)
│   │   ├── common/exception/    (tratamento global de erros)
│   │   ├── common/web/          (health check, formato padrão de erro)
│   │   └── modules/
│   │       ├── identity/        (User, Profile, Permission, login JWT, filtro de auth)
│   │       ├── organization/    (Restaurant, Unit)
│   │       └── catalog/         (Category, Product, ProductVariation, AdditionalGroup)
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── db/migration/        (Flyway: V1 a V12 estrutura, V900 dados demo)
│   ├── src/test/java/...        (testes de integração com Testcontainers: fundação + login)
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx              (tela mínima de verificação de conexão)
│   │   └── index.css
│   ├── package.json
│   ├── vite.config.ts
│   └── Dockerfile
├── docker-compose.yml
├── .env.example
├── README.md
└── CONTINUAR-PROJETO.md
```

---

## Dados de Demonstração

A migration `V900__seed_demo_data.sql` cria automaticamente (apenas em ambiente de
desenvolvimento):
- 1 restaurante e 1 unidade de demonstração
- 6 perfis (Administrador, Gerente, Caixa, Garçom, Cozinha, Motoboy)
- 1 usuário administrador: `admin@demo.local` — **credencial de desenvolvimento apenas**,
  criada exclusivamente pelo seed no profile `dev`, nunca use em produção
- Categorias, produtos e mesas de exemplo
- 2 zonas de entrega de exemplo

A senha do usuário de demonstração é definida apenas no seed de desenvolvimento
(`V900__seed_demo_data.sql`) e **não é documentada aqui de propósito**. Para rodar os
exemplos `curl` abaixo, exporte-a no seu shell local a partir da sua configuração de dev:

```bash
export DEMO_EMAIL=admin@demo.local
export DEMO_PASSWORD='<senha definida no seu ambiente de dev>'
```

**Antes de qualquer deploy em produção**, essa migration deve ser revisada e isolada
por profile — isso está documentado como pendência em `CONTINUAR-PROJETO.md`.

---

## O Que Está Implementado Nesta Etapa

| Item | Status |
|---|---|
| Estrutura de projeto (backend + frontend) | Implementado |
| Configuração Docker + Docker Compose | Implementado |
| Todas as migrations Flyway (estrutura completa do MVP + Fase 1.5) | Implementado |
| Segurança básica (BCrypt, sessão stateless, CORS configurável) | Implementado |
| Tratamento global de erros (formato padronizado) | Implementado |
| Health check | Implementado |
| Teste de integração validando todas as migrations | Implementado e testado |
| **Módulo `identity`**: entidades `User`/`Profile`/`Permission` (JPA) | Implementado |
| **Login com JWT real** (`POST /api/auth/login`) | Implementado e testado |
| Bloqueio de conta após 5 tentativas incorretas (15 min) | Implementado |
| Autorização por perfil/permissão nos endpoints (JWT já popula authorities) | Base pronta — uso em `@PreAuthorize` começa nos controllers de negócio (Etapa 6) |
| Tela de login no frontend (fluxo completo contra o backend real) | Implementado |
| **Módulo `organization`**: `Restaurant`, `Unit` — CRUD de unidades (`/api/units`) | Implementado |
| **Módulo `catalog`**: `Category`, `Product`, `ProductVariation`, `AdditionalGroup`, `Additional` | Implementado |
| Endpoints de catálogo (`/api/categories`, `/api/products`, `/api/additional-groups`) | Implementado, protegidos por `@PreAuthorize` (ADMINISTRADOR/GERENTE escrevem, qualquer autenticado lê) |
| **Módulo `dinein`**: `Table`, `Service` (atendimento), `Command` | Implementado |
| Máquinas de estado completas (mesa, atendimento, comanda) com validação de transição | Implementado |
| Concorrência: uma mesa não pode ter dois atendimentos ativos (índice do banco + validação de aplicação) | Implementado e testado |
| Endpoints `/api/tables`, `/api/services`, `/api/commands` | Implementado |
| **Módulo `ordering`**: `Order`, `OrderItem`, `OrderItemAdditional`, `OrderStatusHistory` | Implementado |
| Preço sempre buscado do catálogo e congelado no pedido (nunca aceito do frontend) | Implementado e testado |
| Máquina de estado completa de `Order` e `OrderItem` | Implementado |
| **Módulo `kitchen`**: painel de produção (`/api/kitchen/tasks`) | Implementado |
| WebSocket (STOMP) para atualização em tempo real do painel da cozinha | Implementado — handshake autenticado por JWT (mesmo token do REST, validado no frame CONNECT) |
| Status do pedido recalculado automaticamente a partir dos itens da cozinha | Implementado e testado |
| **Módulo `payments`**: registro de pagamento, saldo do pedido | Implementado |
| **Regra "comanda não fecha com saldo devedor"** | Implementado e testado — fechamento automático da comanda ao completar o pagamento |
| **Módulo `cashregister`**: abrir/fechar caixa, sangria, suprimento, conferência | Implementado e testado |
| **Módulo `customers`**: cliente + múltiplos endereços | Implementado |
| **Módulo `delivery`**: `DeliveryZone`, `Delivery`, `DeliveryAttempt` | Implementado |
| Canal `DELIVERY` em pedidos (antes bloqueado de propósito) | Implementado — taxa calculada pela zona do bairro, somada ao total |
| **Módulo `couriers`**: cadastro e status de entregador | Implementado |
| Aceite concorrente de entrega ("dois entregadores, só um consegue") | Implementado e testado — optimistic locking + HTTP 409 |
| Confirmação de entrega por código | Implementado e testado |
| **Módulo `inventory`**: ingredientes, fichas técnicas, compras | Implementado |
| **Baixa automática de estoque** (ao concluir item na cozinha) | Implementado e testado |
| **Módulo `reports`**: faturamento diário, ticket médio, produtos mais vendidos, estoque baixo | Implementado |
| **Módulo `notifications`**: notificações internas (ex.: "pedido pronto") | Implementado |
| Integrações externas (WhatsApp, e-mail, SMS, push) | Fora de escopo — decisão da Etapa 1, não implementar sem aprovação |
| Integrações fiscais | Fora de escopo desta versão |

**Com a Fase 2, todos os módulos previstos no escopo original do projeto estão implementados.**
O que resta são refinamentos: dívidas técnicas registradas ao longo do desenvolvimento
(ver `CONTINUAR-PROJETO.md`), testes de carga/concorrência mais realistas, e a conclusão
da migração do frontend para Angular (staff e jornada do cliente já portados; aposentar o
React após confirmação de paridade).

**🎉 Com a Etapa 9, o MVP definido na Etapa 2 está completo:**
`identity → organization → catalog → dinein → ordering → kitchen → payments → cashregister`
O fluxo de salão (mesa → atendimento → comanda → pedido → cozinha → pagamento → caixa)
funciona de ponta a ponta.

### Testando o fluxo de pagamento manualmente

```bash
# Abrir caixa
curl -X POST http://localhost:8080/api/cash-registers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":1,"openingBalance":100.00}'

# Registrar pagamento (após criar um pedido, ver seção de pedidos)
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"orderId":1,"method":"DINHEIRO","amount":21.00,"cashRegisterId":1}'
```

### Testando o fluxo de delivery manualmente

```bash
# Cliente + endereço
CUSTOMER_ID=$(curl -s -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"fullName":"Cliente Teste","phone":"11999998888"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -X POST http://localhost:8080/api/customers/$CUSTOMER_ID/addresses \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"street":"Rua das Flores","number":"100","neighborhood":"Centro","city":"Sao Paulo","state":"SP","zipCode":"01000-000"}'

# Pedido DELIVERY (taxa da zona "Centro" do seed = R$ 5,00, pedido mínimo R$ 20,00)
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":1,"channel":"DELIVERY","customerId":1,"customerAddressId":1,"items":[{"productId":2,"quantity":1}]}'
```

### Testando estoque e relatórios manualmente

```bash
# Cadastrar ingrediente e ficha técnica
curl -X POST http://localhost:8080/api/inventory/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":1,"name":"Pao Brioche","unitOfMeasure":"UN","minimumQuantity":10}'

curl -X POST http://localhost:8080/api/inventory/recipes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"productId":1,"items":[{"inventoryItemId":1,"quantity":1}]}'

# Relatórios
curl http://localhost:8080/api/reports/top-products?unitId=1 -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/api/reports/low-stock?unitId=1 -H "Authorization: Bearer $TOKEN"
```

### Segurança do WebSocket

O endpoint `/ws` (painel da cozinha em tempo real) valida o JWT no handshake STOMP:
`WebSocketAuthChannelInterceptor` verifica o header `Authorization: Bearer <token>` no
frame `CONNECT` usando o mesmo `JwtService` do REST. Conexões sem token válido são
recusadas — o painel em tempo real exige login, como o restante da API.

### Testando o fluxo de pedido + cozinha manualmente

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Após abrir atendimento e comanda (ver seção de salão), criar um pedido:
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":1,"channel":"SALAO","commandId":1,"items":[{"productId":1,"quantity":2,"notes":"Sem cebola"}]}'
```

### Testando o fluxo de salão manualmente

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Abrir atendimento na mesa 1 (mesa "01" do seed, deve estar LIVRE)
curl -X POST http://localhost:8080/api/services \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"tableId":1,"partySize":2,"notes":"Teste manual"}'
```

### Testando o catálogo manualmente

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl http://localhost:8080/api/products?unitId=1 -H "Authorization: Bearer $TOKEN"
```

### Testando o login manualmente

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}"
```

Deve retornar um JSON com `token`, `tokenType`, `expiresInMinutes` e os dados do usuário.
Usuário/senha de demonstração — **nunca usar em produção.**

---

## Próximos Passos

Ver `CONTINUAR-PROJETO.md` para o roteiro detalhado de continuação.
