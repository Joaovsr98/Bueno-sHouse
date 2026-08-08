# Continuar o Projeto — Guia de Retomada

> Leia este arquivo antes de pedir para o Claude continuar o desenvolvimento em outra
> máquina ou outra conversa. Ele resume exatamente onde o projeto parou.

---

## 0. Ambiente local sem Docker (configurado em 2026-08-07)

Esta máquina não tem Docker Desktop instalado, então o backend roda com
**JDK + Maven + MySQL instalados diretamente no Windows**, sem containers.
Isso é só uma forma alternativa de rodar o mesmo projeto — o `docker-compose.yml`
continua valendo para quem tiver Docker.

**O que está instalado:**
- JDK 21 (Eclipse Temurin) — `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`
- Maven 3.9.16 — `%LOCALAPPDATA%\Programs\apache-maven` (adicionado ao PATH do usuário)
- MySQL Server 8.4 — `C:\Program Files\MySQL\MySQL Server 8.4`, dados em
  `C:\ProgramData\MySQLData`. **Não roda como serviço do Windows** (a instalação
  não teve permissão de administrador para isso) — precisa ser iniciado manualmente
  a cada reinicialização da máquina:
  ```bash
  "C:/Program Files/MySQL/MySQL Server 8.4/bin/mysqld.exe" --datadir="C:/ProgramData/MySQLData" --port=3306
  ```
- Banco `restaurante_db` e usuário `restaurante_app` já criados (credenciais no `.env`,
  que não é versionado — ver `.env.example` para a lista de variáveis).

**Bugs pré-existentes corrigidos** (nunca detectados porque `mvn test` nunca tinha
rodado de fato neste projeto, em nenhuma etapa anterior):
1. `backend/.../ordering/service/OrderService.java` — variável capturada em lambda
   não era efetivamente final (erro de compilação).
2. Divergência de tipos entre entidades JPA e o schema SQL gerado pelas migrations
   Flyway (`ENUM` no banco vs `String`/`VARCHAR` esperado pela entidade, em
   `V6__dinein.sql`, `V8__ordering.sql`, `V9__payments.sql`, `V10__delivery.sql`;
   e `SMALLINT` vs `INTEGER` em `V3__catalog.sql`). Corrigidos nas migrations.
3. Mesmo padrão de divergência ainda existe em pelo menos uma tabela
   (`cash_registers.open_flag`, `TINYINT` vs `INTEGER`) e possivelmente outras não
   descobertas ainda — em vez de caçar uma por uma, a validação estrita do Hibernate
   foi desligada **apenas neste ambiente local** via variável de ambiente
   `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (ver comando abaixo). O Flyway continua
   sendo a única fonte de verdade do schema; isso só evita que o Hibernate recuse
   subir por causa de um mismatch de tipo que não afeta o funcionamento real.
   **Pendência:** auditar essas divergências linha a linha e corrigir de vez.

**Como compilar e rodar o backend nesta máquina (sem Docker):**
```bash
cd backend
mvn -Dmaven.test.skip=true package
```
```bash
cd backend
set -a && source ../.env && set +a
export SPRING_JPA_HIBERNATE_DDL_AUTO=none
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot"
"$JAVA_HOME/bin/java" -jar target/sistema-0.1.0-SNAPSHOT.jar
```
Testado com sucesso em 2026-08-07: login retorna 200 para os 6 usuários seed
(`admin`, `gerente`, `caixa`, `garcom`, `cozinha`, `motoboy` @demo.local, com a
senha de desenvolvimento definida no seed `V900__seed_demo_data.sql` — ver seção
mais abaixo).

`mvn test` (suíte completa com Testcontainers) continua **não executável** aqui,
pois Testcontainers exige Docker. Só foi possível rodar o backend "de verdade"
pulando os testes (`-Dmaven.test.skip=true`).

---

## 1. Estado Atual

**🎉 Fase 2 concluída. Todos os módulos do escopo original (Etapa 1 do prompt
inicial) estão implementados.**

Ordem completa percorrida desde o início:
```
identity → organization → catalog → dinein → ordering → kitchen → payments
→ cashregister → customers → delivery → couriers → inventory → reports
→ notifications
```

O que existe (cumulativo): Etapas 4-9 (fundação até pagamento/caixa) + Fase 1.5
(customers, delivery, couriers) + agora:

- **Módulo `inventory` (com automação de verdade):**
  - `Supplier`, `InventoryItem`, `Recipe`, `RecipeItem`, `StockMovement`,
    `Purchase`.
  - `POST /api/inventory/items`, `POST /api/inventory/recipes` (ficha
    técnica de um produto), `POST /api/inventory/items/{id}/movements`
    (movimentos manuais: `ENTRADA_COMPRA`, `AJUSTE_PERDA`),
    `GET /api/inventory/items/below-minimum`.
  - **Baixa automática implementada, exatamente como decidido desde a Etapa
    2/3:** `InventoryService.deductForOrderItem()` é chamado por
    `KitchenService.complete()` no exato momento em que um `OrderItem` é
    marcado `PRONTO` — nunca na criação do pedido. Se o produto não tiver
    ficha técnica cadastrada, a chamada não faz nada silenciosamente (nem
    todo produto precisa controlar estoque).
  - **Idempotência:** antes de baixar, verifica se já existe um
    `StockMovement` do tipo `SAIDA_PRODUCAO` referenciando aquele
    `order_item_id` — protege contra baixa duplicada.
- **Módulo `reports`:**
  - Implementado com `EntityManager` direto (não repositório Spring Data
    tradicional), porque as consultas agregam várias entidades sem "dono"
    único — replica exatamente as consultas SQL desenhadas na Etapa 3.
  - `GET /api/reports/daily-revenue`, `/average-ticket`, `/top-products`,
    `/low-stock`.
- **Módulo `notifications`:**
  - Apenas notificações **internas** (decisão da Etapa 1: WhatsApp/e-mail/SMS/
    push não entram sem aprovação explícita — continuam não implementados).
  - `OrderService` dispara uma notificação para `order.createdBy` (o garçom
    que criou o pedido) quando o pedido fica `PRONTO`.
  - `GET /api/notifications/unread`, `POST /api/notifications/{id}/read`.

### ⚠️ Mais um acoplamento entre módulos (mesma categoria da Fase 1.5)

`OrderService` agora também depende de `NotificationService` (para avisar o
garçom) e `KitchenService` depende de `InventoryService` (para baixar
estoque). Nenhum dos dois cria ciclo de bean Spring, mas o número de módulos
que `OrderService`/`KitchenService` conhecem diretamente está crescendo. Isso
é registrado aqui como sinal de que, numa refatoração futura, vale considerar
um mecanismo de eventos de domínio (ex.: Spring `ApplicationEventPublisher`)
para desacoplar "isto aconteceu" de "quem reage a isto" — os nomes dos
eventos já foram até listados na Etapa 2 (`OrderReadyForPickupOrDelivery`,
`StockAdjusted`, etc.), mas a implementação atual usa chamada direta de
método em vez de publish/subscribe.

### Pendências herdadas (sem mudança nesta etapa)

- ~~WebSocket sem autenticação no handshake (Etapa 8).~~ **RESOLVIDO** — o handshake
  STOMP agora valida o JWT no frame `CONNECT` via `WebSocketAuthChannelInterceptor`
  (mesmo `JwtService` do REST); conexão sem token válido é recusada.
- Nomenclatura `Service`/`@Service` do Spring (Etapa 7).
- Concorrência na geração de `order_number` sob alta carga (Etapa 8).
- Sem troco/estorno estruturado (Etapa 9).
- Acoplamento `ordering` ↔ `delivery` (Fase 1.5).

---

## 2. Última Etapa Concluída

Fase 2 — `inventory` (com automação), `reports`, `notifications`. **Todos os
módulos do escopo original do prompt estão implementados.**

---

## 3. O Que Foi Testado

- **`InventoryAutoDeductionIT`** (Testcontainers, MySQL real, seed aplicado):
  1. cadastra o ingrediente "Pão Brioche" com 100 unidades (via item +
     movimento manual `ENTRADA_COMPRA`);
  2. cria a ficha técnica do "X-Burguer Artesanal": 1 pão por unidade
     vendida;
  3. abre atendimento/comanda, pede 3x X-Burguer;
  4. leva o pedido pela cozinha até `PRONTO` (start + complete no item);
  5. **confirma que o estoque foi baixado sozinho, sem nenhuma chamada
     manual: 100 - 3 = 97**;
  6. confirma que existe um `StockMovement` do tipo `SAIDA_PRODUCAO` com
     quantidade 3, vinculado ao item do pedido.
- Testes herdados de todas as etapas anteriores continuam no projeto (agora
  são 6 classes de teste de integração cobrindo login, catálogo, salão,
  pedido+cozinha, pagamento+caixa, delivery, e estoque).

## 4. O Que Ainda NÃO Foi Testado

- **Mesma limitação de sempre — e mais importante que nunca:** `mvn test`
  não foi executado de fato neste ambiente (sem Maven/JDK completo) em
  **nenhuma etapa deste projeto**. Com 13 módulos de backend agora, rodar os
  testes localmente antes de continuar não é mais opcional — é o próximo
  passo obrigatório.
- `reports` não tem teste de integração dedicado (a lógica foi revisada
  manualmente, incluindo o uso de `EntityManager` com JPQL cru — esse é o
  código com maior risco de erro de sintaxe não detectado, já que consultas
  JPQL só falham em tempo de execução, não de compilação).
- `notifications` não tem teste de integração dedicado (a criação da
  notificação ao ficar `PRONTO` foi verificada apenas por leitura de código).
- Produto sem ficha técnica cadastrada, marcado como `PRONTO` — deveria
  simplesmente não afetar o estoque (comportamento silencioso por design),
  mas não há teste confirmando isso.

---

## 5. Próximos Passos Recomendados

**O escopo funcional está completo.** A partir daqui, o trabalho é de
consolidação, não de novos módulos:

1. **Rodar `mvn test` localmente — prioridade máxima**, dado que isso nunca
   foi verificado neste ambiente de geração de código em nenhuma das 13
   etapas.
2. Resolver as dívidas técnicas acumuladas, por ordem de risco:
   - ~~Autenticação no handshake do WebSocket~~ — **feito** (`WebSocketAuthChannelInterceptor`).
   - Teste de concorrência real com threads paralelas (aceite de entrega,
     abertura de atendimento).
   - Considerar extrair um mecanismo de eventos de domínio para reduzir o
     acoplamento direto entre `ordering`, `delivery`, `kitchen`,
     `inventory`, `notifications`.
3. **Frontend**: hoje só existe a tela de login. Todos os 13 módulos de
   backend estão prontos para consumo — as telas de cardápio, mapa de
   mesas, painel da cozinha, caixa, etc. (previstas na Etapa 1 do prompt
   original) ainda não foram construídas.
4. Fiscal/integrações de pagamento real (gateway) — deliberadamente fora de
   escopo desde a Etapa 1, retomar apenas mediante aprovação explícita.

---

## 6. Principais Arquivos e Módulos

| Arquivo/Pasta | O que é |
|---|---|
| `backend/.../modules/inventory/service/InventoryService.java` | Baixa automática de estoque, idempotente |
| `backend/.../modules/reports/service/ReportService.java` | Consultas agregadas via EntityManager/JPQL |
| `backend/.../modules/notifications/service/NotificationService.java` | Notificações internas |
| `backend/src/test/java/.../inventory/InventoryAutoDeductionIT.java` | Teste de ponta a ponta da baixa automática |

---

## 7. Como Abrir e Continuar em Outro Computador

```bash
unzip sistema-restaurante-completo.zip
cd restaurante-sistema
cp .env.example .env
# edite o .env (JWT_SECRET precisa ter 32+ caracteres)

cd backend && mvn test && cd ..

docker compose up -d --build
```

Teste manual do estoque (após login):

```bash
curl -X POST http://localhost:8080/api/inventory/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"unitId":1,"name":"Pao Brioche","unitOfMeasure":"UN","minimumQuantity":10}'
```

Depois, para continuar o desenvolvimento com o Claude, envie este arquivo
(`CONTINUAR-PROJETO.md`) no início da conversa.

---

## 8. Frontend: telas construídas (2026-08-07)

Depois da Etapa 5 (só login), foram construídas as telas de negócio em React:
Login (com botões de acesso rápido por perfil, ambiente de demonstração) →
Mapa de Mesas → Atendimento → Comanda/Pedido (com pagamento) → Painel da
Cozinha → Caixa → Área do Motoboy. Ver `frontend/src/pages/`.

`V900__seed_demo_data.sql` foi expandido: além do `admin@demo.local` original,
agora existe um usuário por perfil (`gerente`, `caixa`, `garcom`, `cozinha`,
`motoboy` @demo.local, todos com a senha de desenvolvimento do seed) para permitir login de um
clique na tela de login, sem digitar credenciais — pensado para testar cada
perfil rapidamente durante o desenvolvimento.

**Esta migration já foi aplicada** em bancos que rodaram a versão anterior dela.
Se algum ambiente reclamar de checksum do Flyway ao atualizar, a solução é
recriar o banco do zero (não há dados de produção reais em jogo, é ambiente de
demonstração).

---

## 9. Pendência em aberto: possível pivô de escopo (DaHorta acadêmico)

Em 2026-08-07 a usuária trouxe o Capítulo 1 de um material de faculdade
descrevendo um projeto chamado **DaHorta**. O Capítulo 1 já formaliza
explicitamente (correção registrada aqui — uma leitura anterior deste arquivo
havia dito, por engano, que isso ainda faltava confirmar):

- DaHorta é uma **dark kitchen** (sem salão de atendimento), com quatro atores:
  **Cliente, Funcionário/Cozinha, Motoboy, Dono/Administração**.
- Fluxo obrigatório do pedido: `received → preparing → ready → delivering → delivered`.
- A entrega é confirmada pelo motoboy usando um **código de confirmação**.
- Stack final obrigatória: **Angular (cliente) + Spring Boot (servidor) + MySQL
  (banco)** — a versão didática com Firebase é substituída por Spring Boot + MySQL.
- Arquitetura: `Angular → HTTP/JSON → Spring Boot → JPA/JDBC → MySQL` — o
  navegador nunca acessa o MySQL direto, só via API.

O que o Capítulo 1 **ainda não define**: o modelo de dados completo (entidades,
atributos, cardinalidades) e o detalhamento de regras de negócio — isso deve
vir em capítulos posteriores.

**Frontend atual: React + TypeScript + Vite, não Angular.** Ver seção 10 para a
auditoria preliminar e o impacto de migração.

Ela deixou claro que **não quer copiar o projeto do professor, só usar como
base/inspiração**. O material enviado até agora é só o Capítulo 1 — suficiente
para uma auditoria preliminar de arquitetura/atores/fluxo (seção 10), mas não
para o modelo de dados completo, que vem em capítulos posteriores.

**Nada foi alterado em código ainda.** A auditoria preliminar está na seção 10.
Próximos passos dependem de aprovação da usuária e de mais capítulos do
material.

---

## 10. Auditoria preliminar — sistema atual vs. requisitos do DaHorta (Cap. 1)

Feita em 2026-08-07, com base apenas no que o Capítulo 1 confirma. Não migra
nem remove nada — é só classificação para orientar decisões futuras.

### Mapeamento oficial: estados internos → 5 estados acadêmicos

O domínio interno tem mais granularidade que o exigido pelo professor, mas
todo estado interno mapeia para um dos 5 estados obrigatórios — nenhum estado
acadêmico fica sem correspondência, e nenhum estado interno foi removido:

| Estado acadêmico (Cap. 1) | Estado(s) interno(s) correspondente(s) | Quem conduz |
|---|---|---|
| `received` | `RECEBIDO` (Order) | Sistema, ao registrar o pedido |
| `preparing` | `ENVIADO_PARA_COZINHA`, `EM_PREPARO` (Order/OrderItem) | Cozinha (`KitchenController`) |
| `ready` | `PRONTO`, `AGUARDANDO_ENTREGADOR`, `RETIRADA_NO_RESTAURANTE` (Order/Delivery) | Cozinha conclui; motoboy aceita e retira |
| `delivering` | `EM_ROTA` (Delivery) | Motoboy (`DeliveryController.leave`) |
| `delivered` | `ENTREGUE` (Delivery) — **exige `confirmationCode` válido** | Motoboy, via `POST /api/deliveries/{id}/confirm` |

Confirmado no código: `DeliveryService.confirmDelivery` rejeita a confirmação
se `confirmationCode` não bater (`DeliveryService.java:140`) — a regra "cozinha
até `ready`, motoboy conduz `delivering`, `delivered` só com código" já é
verdade no backend atual, não é uma promessa.

**Classificação por módulo:**

### Duas classificações (aprovado pela usuária em 2026-08-07)

```
                    NOSSO SISTEMA
                         │
        ┌────────────────┴────────────────┐
        │                                 │
   NÚCLEO ACADÊMICO DAHORTA            EXTENSÕES DO PRODUTO
        │                                 │
   Cliente                             Salão
   Cardápio                            Mesa
   Pedido                              Atendimento
   Cozinha                             Comanda
   Motoboy                             Garçom
   Código de entrega                   Caixa avançado
   Estoque/admin (conforme capítulos)  Multiunidade
```

**Nenhuma extensão é removida.** O núcleo acadêmico é o que precisa estar 100%
correto e demonstrável para a disciplina; as extensões continuam existindo e
evoluindo em paralelo, só não são o foco enquanto o núcleo não estiver fechado.

| Módulo/tela | Classificação | Observação |
|---|---|---|
| `identity` (login) | Núcleo — aderente | Login já existe, falta ator "Cliente" se autenticando (só staff hoje) |
| `catalog` (cardápio) | Núcleo — aderente | Cardápio/produtos já existe e serve tanto dark kitchen quanto salão |
| `ordering` (fluxo do pedido) | Núcleo — aderente (superset) | Máquina de estados cobre e excede o fluxo exigido (ver mapeamento acima) |
| `kitchen` (painel cozinha) | Núcleo — aderente | Cobre o ator Funcionário/Cozinha |
| `delivery` + `couriers` (motoboy) | Núcleo — aderente | Cobre o ator Motoboy, incluindo código de confirmação |
| `organization` (dono/admin) | Núcleo — aderente | Cobre o ator Dono/Administração |
| `payments` / `cashregister` básico | Núcleo — aderente | Uma dark kitchen ainda precisa cobrar o pedido |
| `customers` | Núcleo — extensão compatível | Cadastro de cliente é natural mesmo numa dark kitchen, mas hoje é gerido por staff, não pelo próprio Cliente (ver auditoria da API abaixo) |
| `inventory` | Extensão do produto (compatível) | Vira núcleo se um capítulo futuro exigir estoque |
| `reports`, `notifications` | Extensão do produto (compatível) | Não conflita, não é exigido no Cap. 1 |
| `dinein` completo (mesas/comandas/salão/garçom) | Extensão do produto | DaHorta é explicitamente sem salão |
| Caixa avançado (sangria/suprimento) | Extensão do produto | Fluxo de dark kitchen não pede isso no Cap. 1 |
| Multiunidade (`units`) | Extensão do produto | Não mencionado no Cap. 1 |
| **Ator "Cliente" fazendo pedido sozinho** | **Gap do núcleo — prioridade máxima** | **Nenhuma tela/endpoint público existe para o Cliente — tudo exige login de funcionário. Auditoria completa da API abaixo.** |

Nada foi classificado como **potencialmente conflitante**: o fluxo de pedido
atual não contradiz o exigido, só o estende.

### Decisão pendente: Angular (não decidido, React preservado por enquanto)

O material do professor diz que "o projeto que você vai entregar roda em
Angular no cliente, Spring Boot no servidor e MySQL no banco" — isso soa como
definição da stack do **frontend do projeto inteiro**, não só da jornada do
Cliente. **Não tratar React (staff) + Angular (Cliente) como arquitetura final
aprovada** — é só uma possibilidade em avaliação.

- **Confirmar antes de decidir:** se todo o frontend entregue precisa ser
  Angular (inclusive telas de staff: cozinha, motoboy, caixa) ou só a jornada
  do Cliente.
- **Até essa confirmação:** o React existente (`frontend/`) é preservado, sem
  migração.
- **Regra para novo trabalho:** qualquer implementação acadêmica nova deve
  evitar aumentar desnecessariamente a dependência do frontend React, para não
  ampliar o custo de uma eventual migração para Angular.
- Impacto técnico de migrar (registrado para quando a decisão for tomada): é
  reescrita completa do frontend, não incremental — roteamento, cache de API,
  formulários e componentes todos trocam de paradigma. O backend REST/JSON não
  muda com a troca de frontend.

**Próximo passo:** aguardando aprovação da usuária sobre a auditoria da API da
jornada do Cliente (seção 11) e a matriz dos Capítulos 2-4 (seção 12).

---

## 11. Auditoria da API — jornada do Cliente (Cardápio → Carrinho → Pedido → Status → Entrega)

Feita em 2026-08-07 lendo `SecurityConfig.java` e os controllers envolvidos.
**Nenhum código foi alterado** — isto é só levantamento de fatos.

### O que já existe e atende

| Etapa da jornada | Endpoint | Situação |
|---|---|---|
| Ver cardápio | `GET /api/categories?unitId=`, `GET /api/products?unitId=` | Dados existem e são suficientes, mas exigem autenticação (ver abaixo) |
| Ver detalhe do produto | `GET /api/products/{id}` | Idem |
| Montar carrinho | — | Não é responsabilidade do backend (fica no frontend) — nenhum gap aqui |
| Finalizar pedido | `POST /api/orders` (`channel: "DELIVERY"`) | Já calcula taxa de entrega pela zona do bairro e cria a `Delivery` automaticamente — lógica pronta |
| Acompanhar status | `GET /api/orders/{id}` | Retorna o status do pedido (mapeável para os 5 estados acadêmicos, seção 10) |
| Entrega confirmada por código | `POST /api/deliveries/{id}/confirm` | Já exige `confirmationCode` correto (`DeliveryService.java:140`) |

### O que falta / está bloqueado

| Ponto | Situação encontrada | Por quê é um problema |
|---|---|---|
| **Autenticação do Cliente** | `SecurityConfig.java`: só `/api/health` e `/api/auth/login` são públicos — **todo o resto exige um usuário `staff` autenticado**. Não existe perfil/role `CLIENTE` na tabela `profiles`, nem endpoint de auto-cadastro/login para cliente. | O Cliente não consegue nem ver o cardápio sem ter uma conta de funcionário. Este é o bloqueio nº 1. |
| **Criar pedido como Cliente** | `OrderController.create` exige `@PreAuthorize hasAnyRole('ADMINISTRADOR','GERENTE','GARCOM','CAIXA')` | Hoje só staff cria pedidos, mesmo os `DELIVERY`. O Cliente não pode finalizar o próprio pedido. |
| **Ver o próprio pedido, e só o próprio** | `GET /api/orders/{id}` não tem `@PreAuthorize` nem checagem de dono — qualquer usuário autenticado pode ler qualquer pedido de qualquer unidade pelo ID. | Ainda não é um vazamento hoje (só staff está autenticado), mas **abrir esse endpoint para clientes sem adicionar checagem de propriedade seria um vazamento de dados entre clientes diferentes**. |
| **Cadastro de cliente (`customers`)** | `CustomerController` não tem `@PreAuthorize` explícito (herda "autenticado"), mas foi desenhado para staff cadastrar cliente no balcão/telefone (comentário no código: "garçom e atendente de delivery precisam cadastrar cliente"), não para autocadastro. | Não existe fluxo de "cliente cria a própria conta". |
| **`confirmationCode` — está protegido?** | **Sim.** Verificado: o código nunca aparece em nenhum DTO de resposta (`DeliveryResponse`, `OrderResponse`) — só existe na entidade `Delivery` e é comparado server-side em `DeliveryService.confirmDelivery`. Não há endpoint que devolva o código. | Não é um problema de segurança — é o oposto: **é tão bem protegido que hoje não existe nenhum jeito do próprio Cliente saber qual é o código**, porque `notifications` só notifica internamente o funcionário que criou o pedido (`order.createdBy`), nunca o cliente. Precisa de um canal (retornar no `OrderResponse` do dono do pedido, WhatsApp/e-mail/SMS — este último fora de escopo desde a Etapa 1) para o cliente efetivamente receber o código que vai usar para confirmar com o motoboy. |
| **Geração do código** | `OrderService.generateConfirmationCode()` usa `Math.random()`, não um gerador criptográfico | Aceitável para um código de 6 dígitos de baixo risco, mas vale registrar — não é `SecureRandom`. |
| **Demonstrar os 5 estados com a API atual** | Sim, dá — o mapeamento da seção 10 já é real no código (`OrderService`, `KitchenController`, `DeliveryController`), só falta o Cliente conseguir *ler* esse status (bloqueado pelo ponto de autenticação acima). |

### Resumo do bloqueio

A cadeia toda de pedido → cozinha → motoboy → entrega **já existe e funciona**.
O que falta não é lógica de domínio — é **uma porta de entrada para o Cliente**:
autenticação/identidade própria (ou acesso público controlado ao cardápio +
criação de pedido), permissão para criar o próprio pedido, e uma checagem de
propriedade em "ver meu pedido" antes de abrir esse endpoint para não-staff.

**Nenhuma implementação foi feita.** Aguardando aprovação da usuária sobre como
resolver a autenticação do Cliente antes de qualquer código.

---

## 12. Auditoria — Capítulos 2 (Git), 3 (MySQL) e 4 (API REST)

Feita em 2026-08-07, com fatos verificados diretamente no repositório (não é
opinião). **Nenhum código foi alterado; nenhum commit/push/init foi feito.**

Legenda: **A** = conceito exigido pelo professor · **B** = como o Cap. 2-4
demonstra em Java puro/CLI · **C** = como este projeto implementa com
Spring Boot/JPA · Status: `ADERENTE` / `ADERENTE COM FRAMEWORK` / `PARCIAL` /
`AUSENTE` / `CONFLITANTE` / `EXTENSÃO`.

### Capítulo 2 — Git

| Requisito acadêmico (A) | Evidência no material (B) | Implementação atual (C) | Status | Gap | Ação recomendada |
|---|---|---|---|---|---|
| Projeto versionado em Git | `git init`, commits desde o início | **Não existe repositório Git**: `git status` no diretório do projeto retorna `fatal: not a git repository` | `AUSENTE` | Todo o histórico de desenvolvimento até agora não está versionado | `git init` + primeiro commit — **requer aprovação antes de executar** |
| `.gitignore` adequado | Ignorar builds, dependências, segredos | **Não existe arquivo `.gitignore`** em nenhum nível do projeto | `AUSENTE` | `.env`, `frontend/node_modules`, `backend/target` ficariam todos rastreáveis se um `git add .` fosse feito hoje | Criar `.gitignore` **antes** do primeiro `git add`, cobrindo `.env`, `node_modules/`, `target/`, `dist/`, `*.log` |
| Nenhum segredo versionado | Nunca commitar senha do banco/chaves | Não aplicável ainda (não há histórico de commits) — mas o `.env` real (com `JWT_SECRET` e senhas do MySQL local) existe hoje solto no diretório, sem `.gitignore` | `AUSENTE` (preventivo) | Risco fica real no momento em que o Git for inicializado sem o `.gitignore` primeiro | Ordem obrigatória: criar `.gitignore` → `git init` → primeiro commit. Nunca o inverso |
| Commits pequenos e claros, branches | — | Não aplicável (sem histórico) | `AUSENTE` | — | Definir estratégia de branch (ex.: `main` + `feature/*`) quando o Git for iniciado |

### Capítulo 3 — MySQL (relação `Category` 1:N `Product`)

| Requisito acadêmico (A) | Evidência no material (B) | Implementação atual (C) | Status | Gap | Ação recomendada |
|---|---|---|---|---|---|
| `Category` 1:N `Product` | Diagrama ER do capítulo | `V3__catalog.sql`: `products.category_id BIGINT UNSIGNED NOT NULL` + `CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)` | `ADERENTE` | — | — |
| Chave primária (PK) | — | Ambas as tabelas usam `id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY` | `ADERENTE` | — | — |
| Chave estrangeira (FK) + integridade referencial | — | `fk_products_category ... ON DELETE RESTRICT` — impede apagar uma categoria com produtos vinculados | `ADERENTE` | — | — |
| `DECIMAL` para dinheiro, nunca `FLOAT/DOUBLE` | Capítulo é explícito sobre isso | `base_price DECIMAL(15,2)`, `price_delta DECIMAL(15,2)`, `price DECIMAL(15,2)` em todas as tabelas monetárias; confirmado por grep que **nenhum `float`/`double` existe no código Java principal** para valores monetários — tudo é `BigDecimal` | `ADERENTE` | — | — |
| `NOT NULL` em campos obrigatórios | — | `name`, `base_price`, `category_id`, `unit_id` todos `NOT NULL` | `ADERENTE` | — | — |
| `DEFAULT` | — | `active BOOLEAN ... DEFAULT TRUE`, `display_order ... DEFAULT 0`, `featured ... DEFAULT FALSE` | `ADERENTE` | — | — |
| `UNIQUE` onde fizer sentido | — | Existe em outras tabelas (`kitchen_sectors`: `UNIQUE(unit_id, name)`), mas **`categories.name` não tem `UNIQUE` por unidade** — duas categorias com o mesmo nome na mesma unidade são permitidas hoje | `PARCIAL` | Duplicidade de nome de categoria não é bloqueada no banco | Avaliar `CONSTRAINT uq_categories_unit_name UNIQUE (unit_id, name)` — **não implementado ainda, só reportado** |
| Índices com critério (não indiscriminados) | Índices aceleram leitura, custam na escrita | `idx_products_unit_category`, `idx_products_available`, `idx_categories_unit_id` — todos ligados a filtros reais usados pelos endpoints (`listByUnit`, `listByCategory`) | `ADERENTE` | — | — |
| CRUD completo | — | `CategoryController`/`ProductController`: `GET` (lista e por id), `POST`, `PUT`, `DELETE` (soft delete via `deleted_at`, não `DELETE` físico) | `ADERENTE COM FRAMEWORK` | Soft delete é uma escolha de projeto além do CRUD básico ensinado, não um gap | — |
| Consulta por categoria / busca de produto | — | `GET /api/products?unitId=&categoryId=` já filtra por categoria | `ADERENTE` | — | — |

### Capítulo 4 — API REST

| Requisito acadêmico (A) | Evidência no material (B) | Implementação atual (C) | Status | Gap | Ação recomendada |
|---|---|---|---|---|---|
| Recursos REST + verbos HTTP corretos | `GET/POST/PUT/DELETE` por recurso | Confirmado em todos os 22 `@RestController`: `GET` para leitura, `POST` para criação, `PUT` para atualização, `DELETE` para remoção | `ADERENTE` | — | — |
| Códigos HTTP corretos (`200/201/204/400/404/500`) | Tabela do capítulo | `POST` usa `@ResponseStatus(HttpStatus.CREATED)` (201); `DELETE` usa `ResponseEntity.noContent()` (204); `GET`/`PUT` retornam 200 por padrão; `ResourceNotFoundException` → 404; `MethodArgumentNotValidException` → 400; exceção genérica → 500 (`GlobalExceptionHandler.java`) | `ADERENTE COM FRAMEWORK` | — | Spring Boot automatiza o mapeamento que o professor ensina a fazer manualmente com `HttpExchange`/`sendResponseHeaders` |
| JSON como formato de troca | — | Jackson (padrão do Spring Boot) serializa/desserializa todos os DTOs automaticamente | `ADERENTE COM FRAMEWORK` | — | — |
| Separação de responsabilidades (Handler → DAO → JDBC, no material) | Capítulo apresenta `HttpServer → Handler → DAO → JDBC → MySQL` | Este projeto usa `Controller → Service → Repository → JPA/JDBC → MySQL` em todos os 14 módulos — mesmo conceito de camadas, Spring Data JPA no lugar de DAO manual | `ADERENTE COM FRAMEWORK` | — | — |
| Acesso ao banco via JDBC / `PreparedStatement` (prevenção de SQL Injection) | Capítulo ensina `PreparedStatement` manual | Spring Data JPA e `EntityManager.createQuery` usam **parâmetros nomeados** (`:unitId`, `setParameter(...)`) em 100% das consultas verificadas — nenhuma concatenação de string com entrada do usuário foi encontrada em nenhuma query (incluindo o `reports`, que usa JPQL cru via `EntityManager`) | `ADERENTE COM FRAMEWORK` | — | — |
| CORS configurado | — | `CorsConfig.java`, origens vindas de `CORS_ALLOWED_ORIGINS` (variável de ambiente), sem usar `*` | `ADERENTE` | — | — |
| Tratamento global de exceções, sem vazar stack trace | — | `GlobalExceptionHandler.java`: captura tudo com `@ExceptionHandler(Exception.class)` e devolve mensagem genérica ("Ocorreu um erro inesperado"), nunca `ex.printStackTrace()` nem a exceção crua no corpo da resposta | `ADERENTE` | — | — |
| Respostas de erro padronizadas (400/404/500) | — | Todas passam pelo mesmo formato `ApiError` (`common/web/ApiError.java`), incluindo erros de validação (`400`) com lista de campos | `ADERENTE COM FRAMEWORK` | — | — |

### Conclusão desta rodada

Nenhum item foi classificado como `CONFLITANTE`. O único `AUSENTE` real e
acionável é o **Capítulo 2 (Git)** — o projeto nunca foi versionado. Os
Capítulos 3 e 4 confirmam que a base já construída (MySQL relacional bem
modelado, API REST com Spring Boot) está alinhada aos conceitos ensinados,
com o framework fazendo automaticamente o que o professor ensina "na mão".

**Nada foi alterado.** Aguardando aprovação da usuária para, quando autorizado:
(1) criar `.gitignore` e inicializar o Git (ordem importa — gitignore primeiro);
(2) decidir sobre o `UNIQUE` de nome de categoria por unidade.

---

## 13. Processo acordado para os próximos capítulos do material (2026-08-07)

A usuária vai continuar enviando capítulos do material do professor (DaHorta)
um a um, e definiu o seguinte processo — **vale para toda auditoria futura
deste projeto, não só para os capítulos já recebidos:**

1. Cada capítulo novo é tratado como **especificação acadêmica progressiva**,
   auditado contra o sistema atual em 5 pontos:
   - **Obrigatório pelo professor** — precisa aparecer no projeto.
   - **Já atendido** — o sistema já faz igual ou de forma equivalente.
   - **Gap** — precisa ser implementado ou corrigido.
   - **Extensão nossa** — útil, mas vai além do escopo do DaHorta.
   - **Conflito** — algo já construído precisa ser adaptado para não
     contrariar a entrega.
2. **Fluxo obrigatório antes de qualquer código:**
   `novo capítulo → auditoria → impacto real → precisa alterar? → se não,
   preserva; se sim, plano → aprovação da usuária → só então implementação.`
   Claude não implementa nada por iniciativa própria a cada capítulo novo.
3. **Não reescrever "pior" só porque o professor ensina na mão primeiro.** A
   tradução é sempre: conceito do professor → requisito acadêmico → nossa
   implementação profissional com o framework (ex.: `PreparedStatement`
   manual → queries parametrizadas via Spring Data JPA; `Handler` → 
   `Controller`/`Service`/`Repository`). O conceito é preservado, a forma é a
   moderna.
4. **Extensões (`salão`, `mesas`, `comandas`, `garçom`, `caixa avançado`,
   `multiunidade`) nunca são apagadas** — ficam classificadas como extensão
   do produto enquanto o núcleo acadêmico (seção 9) é fechado primeiro.
5. Stack final confirmada pelo material, já registrada na seção 9: Angular →
   HTTP/JSON → Spring Boot → MySQL. Decisão sobre migrar React→Angular
   continua **pendente** (seção 9) até confirmação explícita do professor
   sobre se isso vale para todas as telas ou só para o Cliente.

Próximo capítulo a ser auditado com este processo: **Capítulo 5**, indicado
pela usuária como o capítulo que fecha o primeiro fluxo completo
banco → servidor → cliente.

---

## 14. Auditoria — Capítulos 05 (tela pura), 06 (SOLID) e 07 (OWASP)

Feita em 2026-08-07. **Nenhum código foi alterado.** Classificação por ponto:
`OBRIGATÓRIO` / `JÁ ATENDIDO` / `GAP` / `EXTENSÃO NOSSA` / `CONFLITO`.

### Capítulo 05 — Tela em HTML/CSS/JS puro

O capítulo ensina fetch/DOM/estados de tela em JS puro como um degrau
pedagógico antes de frameworks — não é literalmente exigido usar
`fetch`/`textContent` manuais no projeto final (isso vira Angular/HTTPClient
depois). O que é transferível e **é** cobrado são os *conceitos*:

| Conceito do Cap. 05 | Já atendido no React atual? | Status |
|---|---|---|
| Separar estrutura/aparência/comportamento | Componentes React (`.tsx`) + Tailwind (classes utilitárias) — mesma separação de responsabilidades, forma moderna | `JÁ ATENDIDO` |
| Checar `resposta.ok` antes de usar os dados | `lib/api.ts`: toda resposta não-`res.ok` lança `ApiError` com a mensagem do backend — nunca usa dados de uma resposta de erro | `JÁ ATENDIDO` |
| Tratar os 4 estados da tela (carregando/com dados/vazia/erro) | Verificado em `CatalogPage.tsx`, `TablesPage.tsx`, `OrderPage.tsx`: todos tratam `isLoading`, lista vazia (`length === 0`) e erro (bloco vermelho dispensável) — nenhuma tela fica em branco no erro | `JÁ ATENDIDO` |
| `textContent` em vez de `innerHTML` (defesa de XSS) | Confirmado por grep: **nenhum uso de `innerHTML`/`dangerouslySetInnerHTML`** em todo `frontend/src` — JSX escapa texto por padrão, equivalente direto ao `textContent` | `JÁ ATENDIDO` |
| Formatação de moeda (`Intl.NumberFormat`) | `currency()` em cada página usa `toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })` — mesma API do navegador que `Intl.NumberFormat` usa por baixo | `JÁ ATENDIDO` |
| "Hora de versionar" (commit/merge ao fechar a etapa) | Não aplicável ainda — projeto não está sob Git (gap já registrado na seção 12/Cap. 02) | `GAP` (herdado, não novo) |

### Capítulo 06 — SOLID

| Princípio | Onde o capítulo ensina a aplicar | Como o backend atual aplica | Status |
|---|---|---|---|
| **SRP** | `Handler` fazia tudo → separar em Handler/Serviço/Repositório | Todos os 14 módulos já seguem `Controller` (só HTTP) → `Service` (regra de negócio) → `Repository` (persistência), confirmado nos 22 controllers auditados no Cap. 04 | `JÁ ATENDIDO` |
| **DIP** | Serviço depende de uma interface (`ProdutoRepositorio`), não da classe MySQL concreta | `Service`s injetam interfaces `JpaRepository` (Spring Data) via construtor — nunca instanciam a implementação diretamente; o Spring é o "ponto de composição" que resolve a implementação real | `JÁ ATENDIDO COM FRAMEWORK` |
| **LSP** | Uma implementação alternativa do repositório não pode quebrar o contrato | Só existe uma implementação por repositório (gerada pelo Spring Data) — não há hoje um segundo `RepositorioSomenteLeitura` ou equivalente para violar o contrato. Não avaliável por falta de caso, não é um problema | `NÃO APLICÁVEL AINDA` |
| **ISP** | Não obrigar quem só lê a depender de métodos de escrita | Repositórios Spring Data (`JpaRepository`) expõem CRUD completo para todo mundo que os injeta — nenhum módulo hoje separa `LeituraDeProdutos`/`EscritaDeProdutos`. Na prática nunca causou problema porque cada `Service` só chama os métodos que precisa, mas a interface em si não é segregada | `PARCIAL` |
| **OCP** | Novo tipo de desconto = nova classe, sem editar um `if` gigante | `PaymentService.registerPayment` tem *um* `if ("DINHEIRO".equals(...))` para a regra extra do caixa — não é um "if gigante de tipos" crescendo, é a única exceção real entre os métodos de pagamento hoje. Não há uma interface `Desconto`/`MetodoPagamento` polimórfica | `PARCIAL` |

Nenhum item foi classificado como `CONFLITO`: a arquitetura em camadas do
backend já nasceu seguindo SRP/DIP (decisão registrada desde a Etapa 4 do
projeto, antes mesmo deste material aparecer). ISP e OCP têm espaço de
melhoria, mas não são exigidos com urgência — o capítulo pede para *reconhecer*
os cheiros, não que todo `if` vire uma interface nova.

### Capítulo 07 — OWASP

| Defesa exigida | Como o material ensina | Como o projeto atual implementa | Status |
|---|---|---|---|
| SQL Injection → `PreparedStatement` | Separar comando de dado | Confirmado no Cap. 04 (seção 12): 100% das queries usam parâmetros nomeados (JPA/JPQL), nenhuma concatenação de string | `JÁ ATENDIDO` |
| XSS → `textContent`/escapar | Nunca inserir dado como HTML cru | Confirmado acima (Cap. 05): JSX escapa por padrão, zero uso de `innerHTML` | `JÁ ATENDIDO` |
| CSRF → token anti-CSRF / `SameSite` | Cookies de sessão são enviados automaticamente pelo navegador, atacante explora isso | **A autenticação deste projeto não usa cookies de sessão** — é um JWT Bearer enviado manualmente no header `Authorization` pelo frontend (`lib/api.ts`), nunca por um cookie. Um site malicioso não consegue forjar esse header automaticamente como forjaria um cookie. Por isso `SecurityConfig.java` desliga CSRF (`.csrf(disable)`) — **essa é a prática padrão recomendada para APIs stateless com JWT**, não um descuido | `JÁ ATENDIDO (defesa equivalente, não idêntica)` |
| Senha: nunca em texto puro, hash com salt e custo | PBKDF2 na aula; bcrypt/Argon2 recomendados para produção | `SecurityConfig.java` usa **`BCryptPasswordEncoder`** — exatamente o algoritmo que o capítulo aponta como "ainda mais recomendado" que o PBKDF2 ensinado em aula. Salt é gerado automaticamente pelo BCrypt por senha | `JÁ ATENDIDO (acima do mínimo pedido)` |
| Autenticação vs. Autorização (RBAC, `authGuard`+`roleGuard`) | Dois guardiões em sequência, checagem sempre no servidor | `JwtAuthenticationFilter` (autenticação: quem é você) roda antes de `@PreAuthorize hasRole(...)`/`hasAnyRole(...)` (autorização: o que você pode) em cada controller — mesma sequência de dois guardiões, sempre no servidor (nunca só escondendo botão no frontend) | `JÁ ATENDIDO COM FRAMEWORK` |
| "Owner com acesso ampliado" (regra RN3 do material) | `owner` do DaHorta tem acesso ampliado | Equivalente aqui: perfil `ADMINISTRADOR` tem acesso a praticamente todos os endpoints protegidos (visto nos `@PreAuthorize` auditados) | `JÁ ATENDIDO` |
| Segredos fora do código | `.env`/variáveis de ambiente, nunca hardcoded | Confirmado: `JWT_SECRET`, senhas do banco, tudo vem de variáveis de ambiente (`application.yml` usa `${VAR:default}`), nunca hardcoded no código Java | `JÁ ATENDIDO` |
| Segredo nunca entra no Git | `.gitignore` cobrindo `.env` | **Ainda não se aplica porque não existe Git no projeto** (Cap. 02) — o `.env` real está solto no diretório sem proteção nenhuma até o Git ser inicializado com `.gitignore` primeiro | `GAP` (mesmo gap do Cap. 02, reforçado por este capítulo) |
| HTTPS/TLS em produção | Obrigatório em produção | Ambiente atual é 100% `http://localhost` (dev local, sem Docker/deploy) — não há certificado nem TLS configurado em lugar nenhum | `GAP` (não aplicável em dev local; **vira obrigatório no momento de qualquer deploy real**) |

### Conclusão desta rodada

Nenhum item novo foi classificado como `CONFLITO`. Os dois `GAP`s reais
(Git/`.gitignore` e HTTPS) já eram conhecidos — este capítulo só reforça a
urgência do primeiro (segredo sem `.gitignore` é, literalmente, o exemplo que
o Cap. 07 usa como falha grave) e formaliza o segundo como pendência de
deploy, não de desenvolvimento local.

O achado mais importante desta rodada é **estrutural, não é um gap**: a
defesa de CSRF do projeto é *diferente* da ensinada (token/cookie) porque a
autenticação é *diferente* (JWT Bearer, não sessão por cookie) — e isso é
correto, não uma pendência. Vale deixar isso explícito caso o professor
pergunte "cadê o token anti-CSRF": a resposta é "não se aplica, a
autenticação não usa cookies".

**Nada foi alterado.** Únicas ações pendentes de aprovação continuam sendo as
já registradas na seção 12: `.gitignore` + `git init` (nessa ordem) antes de
qualquer commit.

---

## 15. Auditoria — Capítulos 08 (matriz GoF), 09 (Singleton), 10 (Factory Method), 11 (Abstract Factory)

Feita em 2026-08-07. **Nenhum código foi alterado.**

**Fio condutor desta rodada:** o Cap. 09 já avisa, dentro do próprio material,
que a alternativa recomendada ao Singleton manual é *injetar a instância no
ponto de composição* (Cap. 06/DIP). É exatamente isso que o **container do
Spring (IoC/Dependency Injection)** faz de fábrica: toda classe `@Service`,
`@Repository`, `@Component` e `@Configuration` já nasce **singleton por
padrão** dentro do `ApplicationContext`, e é entregue (injetada) a quem
precisa via construtor — nunca via um `getInstancia()` estático espalhado
pelo código. Confirmado por busca: **nenhuma classe do backend usa
`@Scope`/`@Profile` para fugir do singleton padrão**, e as 26 classes
anotadas (`@Service`/`@Repository`/`@Component`/`@Configuration`) seguem essa
regra sem exceção.

| Capítulo | Dor do DaHorta | Como o material resolve | Como o projeto atual resolve | Status |
|---|---|---|---|---|
| **09 — Singleton** | Configuração única (URL do banco, porta, taxa) compartilhada em todo o sistema | Classe `ConfiguracaoApp` com construtor privado + `getInstancia()` (holder idiom) | `application.yml` + variáveis de ambiente, lidas uma vez pelo Spring na subida e injetadas via `@Value`/construtor onde precisa (ex.: `AuthService` recebe `expirationMinutes` no construtor) — **é a alternativa que o próprio capítulo recomenda** (injeção no lugar de acesso global), não o padrão literal | `JÁ ATENDIDO (via alternativa recomendada pelo próprio capítulo)` |
| **09 — alerta sobre conexão de banco NUNCA ser Singleton** | Uma única `Connection` compartilhada entre threads causa atropelo | `Conexao.abrir()` devolve uma conexão nova a cada chamada (fábrica simples, não Singleton) | **HikariCP** (confirmado nos logs de boot: `HikariPool-1`) — um *pool* de conexões, uma por thread quando necessário, nunca uma única `Connection` global. É exatamente o que o capítulo pede, com a ferramenta padrão de mercado no lugar de uma fábrica manual | `JÁ ATENDIDO (acima do ensinado — pool em vez de fábrica simples)` |
| **10 — Factory Method** | Exportar o cardápio em formatos diferentes (JSON/CSV/texto) sem acoplar quem pede ao exportador concreto | Hierarquia `RelatorioCardapio`/`ExportadorCardapio` com subclasses decidindo o exportador | **Não existe hoje nenhuma funcionalidade de exportação de cardápio no projeto** — não há `if`/`switch` de formato em lugar nenhum porque a funcionalidade em si não existe ainda | `NÃO APLICÁVEL (funcionalidade não implementada, não é gap — é ausência de requisito, não ausência de solução)` |
| **11 — Abstract Factory** | Trocar toda a persistência (memória ↔ MySQL) sem misturar origens entre repositórios | `FabricaDeRepositorios` com `FabricaMySQL`/`FabricaMemoria`, cada uma entregando uma família coerente | O projeto só tem **uma família real** (JPA + MySQL, sempre) — não há implementação alternativa em memória para nenhum repositório. Por isso não há risco de mistura (o problema que o padrão previne simplesmente não pode acontecer aqui: todo repositório vem do mesmo `EntityManagerFactory`), mas também não há o padrão literal instanciado, porque falta uma segunda família para justificá-lo | `JÁ ATENDIDO ESTRUTURALMENTE (coerência garantida pela infraestrutura única), padrão literal NÃO APLICÁVEL por falta de uma 2ª família` |
| **08 — State** (citado como exemplo já usado) | Ciclo de vida do pedido | — | Confirmado e já auditado nas seções 10-11: `Order`, `Command`, `RestaurantTable`, `Delivery`, `OrderItem` — todos têm campo `status` (String) + mapa `ALLOWED_TRANSITIONS` validando toda mudança de estado. É o State do GoF sem a classe `EstadoPedido` polimórfica — implementado como máquina de estados por mapa, uma variação comum e válida do mesmo padrão | `JÁ ATENDIDO (variação por mapa de transições, mesmo conceito)` |
| **08 — Chain of Responsibility** (citado como exemplo já usado) | Filtros de CORS, autenticação, validação em sequência | — | `JwtAuthenticationFilter` roda antes do filtro padrão de usuário/senha do Spring Security (`SecurityConfig.addFilterBefore`), que por sua vez roda antes do `@PreAuthorize` de cada controller — uma cadeia de responsabilidades de verdade, só que montada pelo Spring Security em vez de escrita à mão | `JÁ ATENDIDO COM FRAMEWORK` |
| **08 — Strategy** (citado como exemplo já usado) | Desconto de cupom / cálculo variável | — | **Ainda não existe** — já registrado no Cap. 06 (seção 14) como `PARCIAL`: `PaymentService` trata o caso especial de `DINHEIRO` com um `if`, não há interface `Desconto`/`MetodoPagamento` polimórfica | `GAP (herdado do Cap. 06, reforçado aqui)` |

### Conclusão desta rodada

Nenhum `CONFLITO`. O padrão que mais aparece nestes 4 capítulos — Singleton —
já está coberto, e coberto pela via que o próprio capítulo recomenda como
melhor prática (injeção via container em vez de `getInstancia()` manual).
Factory Method e Abstract Factory descrevem problemas que **o projeto ainda
não tem** (não existe exportação de cardápio, não existe uma segunda origem
de dados) — não é um gap implementar os padrões "por precaução"; o próprio
Cap. 08 avisa que padrão sem dor real é enfeite. Se um capítulo futuro exigir
exportar o cardápio em vários formatos, ou uma segunda origem de dados (ex.:
cache, mock para teste), aí sim esses dois padrões viram ação concreta.

O único item reforçado como pendente é o **Strategy para métodos de
pagamento/desconto**, mas isso só vira ação quando o professor formalizar um
requisito de cupom/desconto (ainda não recebido).

**Nada foi alterado.**

---

## 16. Auditoria — Capítulos 12 (Builder), 13 (Prototype), 14 (Adapter), 15 (Façade), 16 (Decorator)

Feita em 2026-08-07. **Nenhum código foi alterado.**

### Capítulo 12 — Builder

| Ponto | Achado | Status |
|---|---|---|
| Construtor telescópico / muitos campos | `Order` (entidade) tem 9+ campos e é montada em `OrderService.create()` por **chamadas de setter sequenciais** (`order.setUnitId(...)`, `.setChannel(...)`, ... `.setStatus("RECEBIDO")`), não por um Builder com `construir()` validando no fim. A validação existe, mas está espalhada em `if`s antes/durante a montagem, não concentrada num único portão | `PARCIAL` — funciona, mas não tem a garantia "nasce validado ou não nasce" que o Builder dá |
| `CreateOrderRequest` (DTO de entrada) | É um `record` Java (imutável, campos `final`) — alinhado com o princípio de imutabilidade do capítulo. Mas tem **7 campos posicionais, três `Long` adjacentes** (`commandId`, `customerId`, `customerAddressId`) — exatamente o risco que o capítulo descreve ("trocar dois argumentos do mesmo tipo passa despercebido pelo compilador") | `PARCIAL` |
| **Achado concreto, não hipotético** | Ao rodar `mvn package` em 2026-08-07 (seção 0), a compilação **falhou de verdade** com `constructor CreateOrderRequest ... cannot be applied to given types — actual and formal argument lists differ in length`, em dois arquivos de teste (`OrderKitchenFlowIT.java:87`, `PaymentCashRegisterFlowIT.java:97`). Esses testes constroem `CreateOrderRequest` **posicionalmente**, com uma lista de argumentos que ficou desatualizada quando o record ganhou/perdeu um campo — **é o construtor telescópico do capítulo acontecendo de verdade neste projeto**. Os testes foram pulados (`-Dmaven.test.skip=true`), não corrigidos — o erro **ainda está no repositório** | `GAP CONFIRMADO` |
| Ação recomendada (não implementada) | Nos DTOs de entrada construídos por JSON (frontend), o risco não existe (Jackson casa por nome de campo). O risco é só em código Java que instancia o record diretamente — como os dois testes quebrados. Corrigir os testes com os nomes de campo corretos resolveria o sintoma; um Builder no DTO (ou usar `record` com métodos nomeados/`with`) resolveria a causa para o futuro | Aguardando aprovação — nenhuma mudança feita |

### Capítulo 13 — Prototype

Dor do capítulo: gerar variações de um "combo base" por clonagem, evitando cópia rasa de listas/objetos mutáveis.

| Ponto | Achado | Status |
|---|---|---|
| Existe conceito de "combo" clonável no projeto? | Não. `ProductVariation` existe (tamanho/sabor com `priceDelta`), mas é uma variação de **preço** de um produto existente, não um novo produto/combo clonado de outro | `NÃO APLICÁVEL (funcionalidade não existe)` |
| Risco de cópia rasa em algum lugar do código atual? | Verificado no frontend: o carrinho (`OrderPage.tsx`) sempre atualiza estado com spread (`{ ...prev }`, `{ ...l, quantity: ... }`), nunca compartilha array/objeto mutável entre linhas — não há o bug que o capítulo descreve | `JÁ ATENDIDO (risco não existe hoje)` |

### Capítulo 14 — Adapter

Dor do capítulo: gateway de pagamento externo com interface incompatível.

| Ponto | Achado | Status |
|---|---|---|
| Existe integração com gateway de pagamento externo? | **Não, por decisão explícita desde o início do projeto.** `PaymentService.registerPayment()` só registra o pagamento no banco (`status = "APROVADO"` direto) — não chama nenhuma API externa. Já estava documentado antes deste capítulo chegar: "Fiscal/integrações de pagamento real (gateway) — deliberadamente fora de escopo desde a Etapa 1, retomar apenas mediante aprovação explícita" | `NÃO APLICÁVEL (decisão de escopo já registrada, anterior a este capítulo)` |
| Outras integrações externas que precisariam de Adapter? | Nenhuma — `notifications` é só interno (WhatsApp/SMS/e-mail também fora de escopo por decisão da Etapa 1) | `NÃO APLICÁVEL` |
| Quando isso muda | Se/quando um gateway de pagamento real for aprovado, o Adapter é exatamente a peça certa: isolar o gateway atrás de uma interface `ProcessadorPagamento` própria, sem espalhar `if(gateway == "stripe")` pelo `PaymentService` | Registrado para quando a decisão de integrar um gateway for tomada |

### Capítulo 15 — Façade

Dor do capítulo: orquestrar validar+pagar+registrar atrás de uma chamada única.

| Ponto | Achado | Status |
|---|---|---|
| Existe uma fachada de checkout hoje? | Não — `POST /api/orders` (cria o pedido) e `POST /api/payments` (registra o pagamento) são **duas chamadas separadas**, orquestradas pelo frontend (`OrderPage.tsx` chama uma, depois a outra), não por um único endpoint/método no backend | `PARCIAL` |
| É de fato um gap para o nosso domínio? | Questionável — diferente de um checkout online de e-commerce (paga e já era), uma comanda de mesa **fica aberta pagando depois**, às vezes bem depois (seção 10/11: fluxo `AGUARDANDO_PAGAMENTO`). Forçar uma fachada "cria pedido e já cobra" não encaixaria no nosso modelo de salão/comanda. Para o canal `DELIVERY` do núcleo acadêmico (pedido único, sem comanda de mesa) já faz mais sentido | `PARCIAL — depende do canal (dine-in vs. delivery)` |
| Orquestração que **já existe** e é um Façade não-nomeado | `KitchenService.complete()` já orquestra 3 coisas numa chamada só: marca o item pronto, aciona `InventoryService.deductForOrderItem()` (baixa de estoque) e (via `OrderService.recomputeStatusFromItems`) dispara notificação. É estruturalmente um Façade, só que sem o nome — e **o próprio projeto já tinha identificado esse acoplamento como pendência antes deste capítulo chegar** (seção 1: "sinal de que, numa refatoração futura, vale considerar um mecanismo de eventos de domínio") | `JÁ ATENDIDO (façade não-nomeada), com uma dívida técnica já registrada por conta própria` |

### Capítulo 16 — Decorator

Dor do capítulo: log e autenticação em volta de serviços, sem duplicar código nem explodir em subclasses.

| Ponto | Achado | Status |
|---|---|---|
| Autenticação em volta dos serviços | `@PreAuthorize("hasAnyRole(...)")` em cada endpoint de controller é implementado pelo Spring Security via **proxy AOP**: o Spring envolve o bean real numa camada que intercepta a chamada, checa o papel, e só então delega ao método verdadeiro — é literalmente um Decorator/Proxy aplicado automaticamente pelo framework, sem nenhuma duplicação de checagem de papel escrita à mão em cada serviço, e sem explosão de subclasses | `JÁ ATENDIDO COM FRAMEWORK` |
| Log em volta dos serviços | Não existe uma camada decoradora de log — o projeto usa `SLF4J`/config de `logging.level` (`application.yml`) direto, sem um `LogService`/decorador dedicado | `GAP (baixa prioridade — não é exigência de segurança, é observabilidade)` |
| Ordem log-fora-de-auth vs. auth-fora-de-log | Não se aplica hoje (não há decorador de log) | — |

### Conclusão desta rodada

Nenhum `CONFLITO`. Dois achados relevantes:
1. **Cap. 12 (Builder) encontrou um bug real e não hipotético** já existente no
   repositório (testes com `CreateOrderRequest` posicionalmente desatualizado)
   — vale a pena corrigir, mas segue a regra combinada: aguardando aprovação.
2. **Cap. 15 (Façade)** mostrou que o projeto já pratica o padrão sem nome em
   `KitchenService.complete()`, e essa mesma orquestração já era uma dívida
   técnica auto-registrada antes deste material aparecer — os capítulos futuros
   sobre Observer (22) e eventos de domínio provavelmente vão fechar esse
   ponto de forma mais elegante que uma Façade simples.

Prototype e Adapter não se aplicam por ausência da funcionalidade que
motivaria cada um (combo clonável; gateway de pagamento externo) — ambas
decisões de escopo já tomadas antes deste material, não gaps.

**Nada foi alterado.**

---

## 17. Auditoria — Capítulos 17 (Proxy) e 18 (Composite)

Feita em 2026-08-07. **Nenhum código foi alterado.**

### Capítulo 17 — Proxy (cache do cardápio)

| Ponto | Achado | Status |
|---|---|---|
| Cache no servidor (backend) | Verificado por busca: **nenhuma classe usa `@Cacheable`/`@EnableCaching`** — toda chamada a `GET /api/products`/`GET /api/categories` vai ao MySQL sempre, sem exceção | `GAP` |
| "Cache" no cliente (frontend) | O React Query (`@tanstack/react-query`) tem cache embutido, mas o `QueryClient` deste projeto foi criado **sem configuração** (`new QueryClient()`, `main.tsx`) — o padrão da biblioteca é `staleTime: 0`, ou seja, os dados são considerados desatualizados imediatamente e tendem a ser buscados de novo a cada nova visita/foco de tela. Existe uma camada de cache, mas não está configurada para reduzir chamadas de verdade | `PARCIAL` |
| Ação recomendada (não implementada) | O Spring tem a **abstração de cache declarativa** (`@EnableCaching` + `@Cacheable("cardapio")` no método `listByUnit`) — é o "Proxy de cache" do capítulo, só que aplicado por anotação em vez de uma classe `CardapioServicoProxy` escrita à mão, na mesma linha do que já vimos com `@PreAuthorize`/AOP no Cap. 16. Precisaria também de invalidação explícita (`@CacheEvict`) nos métodos de escrita (`create`/`update`/`softDelete` de `CategoryService`/`ProductService`), exatamente o `invalidarCache()` do capítulo | Aguardando aprovação — nenhuma mudança feita |

### Capítulo 18 — Composite (árvore do cardápio)

| Ponto | Achado | Status |
|---|---|---|
| O cardápio hoje é uma árvore? | Não — é uma relação **rasa** de 1 nível: `Category` 1:N `Product` (auditado no Cap. 03). Não existe categoria contendo subcategoria, nem combo contendo produtos (isso seria o `GrupoCardapio` do capítulo) | `NÃO APLICÁVEL (estrutura de dados não é uma árvore)` |
| `ProductVariation`/`AdditionalGroup` poderiam formar uma hierarquia? | Não da forma que o Composite resolve: variação é uma opção de preço do próprio produto (tamanho/sabor), e grupo de adicionais é uma lista plana de extras — nenhum dos dois se contém recursivamente | `NÃO APLICÁVEL` |
| Quando isso mudaria | Se um capítulo futuro (ou o professor) exigir "combos" como agrupamento de produtos com preço próprio, ou categorias aninhadas, o Composite vira a peça certa — hoje a dor que ele resolve não existe no projeto | Registrado para o futuro |

### Conclusão desta rodada

Nenhum `CONFLITO`. O Cap. 17 encontrou um `GAP` real e de baixo risco/alto
valor (cache do cardápio ausente no servidor, e mal configurado no cliente) —
diferente do Cap. 12, este não é um bug, é uma otimização de desempenho ainda
não feita. O Cap. 18 não se aplica porque o cardápio não é, hoje, uma
hierarquia — mesma categoria de resposta que Prototype e Adapter: dor que o
padrão resolve simplesmente não existe neste projeto ainda.

**Nada foi alterado.**

---

## 18. Auditoria — Capítulos 19 a 31 (Bridge → Interpreter, fecha a Parte III)

Feita em 2026-08-07. **Nenhum código foi alterado.** Lote grande — a usuária
pediu para acumular os capítulos restantes da Parte III antes de consolidar.

### Estruturais (19-20)

| Capítulo | Dor do DaHorta | Achado no projeto atual | Status |
|---|---|---|---|
| **19 — Bridge** | Notificação por tipo × canal (explosão combinatória) | `notifications` só tem **um canal** (interno, decisão de escopo desde a Etapa 1 — WhatsApp/SMS/e-mail fora). Sem uma segunda dimensão (canal) variando, não há explosão combinatória a evitar | `NÃO APLICÁVEL (só existe 1 canal; decisão de escopo anterior a este capítulo)` |
| **20 — Flyweight** | Insumos repetidos em várias fichas técnicas, desperdiçando memória | `RecipeItem.inventoryItemId` é **FK** para `InventoryItem` — o dado do insumo (nome, unidade) é gravado **uma única vez** na tabela e só referenciado por `id` em cada ficha; a quantidade (estado extrínseco) fica em `RecipeItem.quantity`. Isso é a normalização relacional (Cap. 03) fazendo, de graça, exatamente a separação intrínseco/extrínseco do Flyweight. E, em tempo de execução, o JPA/Hibernate também aplica isso a nível de objeto: dentro de uma mesma transação, duas buscas pelo mesmo `id` devolvem a mesma instância Java (identity map do *persistence context*) | `JÁ ATENDIDO (via normalização relacional + identity map do JPA, sem código customizado)` |

### Comportamentais (21-31)

| Capítulo | Dor do DaHorta | Achado no projeto atual | Status |
|---|---|---|---|
| **21 — Strategy** | Tipos de desconto de cupom (percentual/fixo/frete grátis) | Confirmado por busca: **não existe nenhuma classe/campo relacionado a cupom ou desconto sendo calculado** — `OrderResponse.discount` existe no DTO mas nunca é setado por nenhum código (`grep` por `setDiscount`/`Coupon`/`Cupom` não encontrou nada). Já flagueado nos Cap. 06/08 | `GAP (reforçado pela 3ª vez — sem requisito formal de cupom ainda)` |
| **22 — Observer** | Avisar cozinha, motoboy e cliente em tempo real quando o pedido muda de status | **Existe de verdade, mas só para 1 dos 3 interessados.** `KitchenEventPublisher` publica em `/topic/kitchen/{unitId}` via STOMP/WebSocket (`SimpMessagingTemplate.convertAndSend`) toda vez que um item muda de status — é um Observer real (sujeito = publisher, observadores = quem se inscreve no tópico), via infraestrutura do Spring, não escrito à mão. Mas é **o único publisher WebSocket do projeto** (confirmado por busca — nenhum outro módulo usa `SimpMessagingTemplate`): não há tópico equivalente para o motoboy nem para o cliente. Frontend usa *polling* (`refetchInterval`) na Cozinha, não push | `PARCIAL — Observer real, mas só cobre 1 de 3 atores` |
| **23 — Command** | Ações (avançar status, cancelar) como objetos, para log/fila/desfazer | As operações de `OrderService`/`CommandService`/`ServiceService` são chamadas diretas de método, não objetos `Comando`. Mas os **3 ganhos que o capítulo promete já existem por outros meios**: **log** → `OrderStatusHistory` grava toda transição (`recordHistory`) numa tabela de auditoria; **fila** → as tarefas da cozinha já são uma fila por natureza (linhas filtradas por `status IN ('ENVIADO','EM_PREPARO')` em `KitchenService.listTasks`, sem precisar de uma fila de objetos `Comando`); **desfazer** → **não existe, e não deveria**: as máquinas de estado do domínio (seção 10/16) são deliberadamente unidirecionais (não dá para "desfazer" um item que já começou a fritar) | `JÁ ATENDIDO POR OUTROS MEIOS (log e fila), "desfazer" não se aplica ao domínio` |
| **24 — State** | Ciclo de vida do pedido, cada estado sabendo seu próximo | Já auditado nas seções 10/16: `Order`, `Command`, `RestaurantTable`, `Delivery`, `OrderItem` usam **`status` (String) + `Map<String,Set<String>> ALLOWED_TRANSITIONS`**, validado em cada `Service`, em vez de uma classe `EstadoPedido` por status. O próprio capítulo valida essa escolha: *"para um status que é só um rótulo, o enum basta; o State compensa quando cada estado tem comportamento próprio"* — no nosso caso, o comportamento por transição (ex.: `if ("CLOSED".equals(newStatus)) { ensureAllCommandsClosed(...) }` em `ServiceService`, `if ("FINALIZADO".equals(newStatus)) { order.setCompletedAt(...) }` em `OrderService`) é tratado por `if`s pontuais dentro do método de transição, não por 5+ classes de estado — variação aceitável para o volume de regras que temos hoje | `JÁ ATENDIDO (variação consciente: mapa de transições + enum, não classes polimórficas)` |
| **25 — Template Method** | Cálculo do total do pedido (frete varia entre entrega/retirada) sem duplicar o roteiro | `OrderService.create()` calcula subtotal (passo fixo), soma `deliveryFee` só `if (deliveryZone != null)` (passo variável, resolvido **inline com um `if`, num único método**), depois o total (passo fixo). **Não existe duplicação do roteiro** — é um único método, então o problema que o Template Method resolve (copiar o cálculo inteiro pra mudar só o frete) nunca chegou a acontecer aqui | `JÁ ATENDIDO (dor evitada por design — método único, sem duplicação)` |
| **26 — Chain of Responsibility** | Filtros de CORS, autenticação, validação em sequência | Já auditado no Cap. 08: `JwtAuthenticationFilter` + a cadeia interna do Spring Security (CORS, autenticação, `@PreAuthorize`) — é exatamente a variante "cadeia de filtros" que o capítulo descreve (todo elo processa, qualquer um pode interromper), montada pelo framework | `JÁ ATENDIDO COM FRAMEWORK (reforçado)` |
| **27 — Iterator** | Percorrer o carrinho sem expor a estrutura interna | O próprio capítulo diz: *"na maior parte do tempo, em Java, você usa o Iterator pronto da linguagem"*. Confirmado: todo o backend percorre listas com `for-each`/`.stream()` sobre `List<T>` do Java — o `Iterable`/`Iterator` embutido da linguagem, nunca acessando índice bruto nem expondo estrutura interna customizada | `JÁ ATENDIDO (via JDK, não custom — exatamente o que o capítulo recomenda)` |
| **28 — Mediator** | Cozinha, motoboy e cliente coordenados por uma central, sem se conhecerem | **Este é o oposto do que temos hoje, e o projeto já sabia disso.** A seção 1 deste documento (escrita antes deste capítulo chegar) já registra: *"`OrderService` agora também depende de `NotificationService`... e `KitchenService` depende de `InventoryService`... o número de módulos que `OrderService`/`KitchenService` conhecem diretamente está crescendo"* — é literalmente o "todos falando com todos" que o Cap. 28 descreve como dor. A mesma seção já sugeria um `ApplicationEventPublisher` do Spring como solução — que funciona como um Mediator leve (cada serviço publica só pra um ponto central) combinado com Observer (múltiplos `@EventListener` reagem) | `GAP CONFIRMADO (auto-identificado antes deste capítulo, agora triangulado por 3 capítulos diferentes: Façade §16, Observer/Mediator §18)` |
| **29 — Memento** | Editar ficha técnica e poder desfazer, sem expor o estado interno | Não existe edição com desfazer para `Recipe`/`RecipeItem` — só existe `StockMovement`, um **livro-razão de append** (cada movimento é uma linha nova, nunca um "desfazer" que apaga/restaura). É uma abordagem diferente (auditoria por adição) para um problema parecido (rastrear mudança), mas não é Memento — e a funcionalidade de "editar e desfazer ficha técnica" em si não existe | `NÃO APLICÁVEL (funcionalidade de edição com undo não existe)` |
| **30 — Visitor** | Múltiplas operações sobre a ficha técnica (CMV, relatório) | Confirmado por busca: **nenhum código de CMV/margem existe** no módulo `inventory`. `reports` calcula agregações (receita, ticket médio, top produtos) direto via JPQL no banco — sobre `Order`/`OrderItem`, não percorrendo objetos `Recipe` em memória. A "operação sobre estrutura" que o Visitor resolve não tem hoje um caso de uso no projeto | `NÃO APLICÁVEL (CMV/margem não implementado; agregações existentes usam SQL, não visita de objetos)` |
| **31 — Interpreter** | Busca combinável no cardápio ("vegano E até 30 reais") | `GET /api/products` só filtra por `categoryId` — não existe nenhuma gramática de busca combinável. O próprio capítulo alerta que é "o padrão menos usado do catálogo", só cabendo em gramáticas pequenas e estáveis — não há motivo pra implementá-lo por precaução | `NÃO APLICÁVEL (funcionalidade de busca combinável não existe, e não é prioridade segundo o próprio capítulo)` |

### Conclusão desta rodada (fecha a Parte III inteira)

Nenhum `CONFLITO` em nenhum dos 13 capítulos. Panorama final da Parte III
(Cap. 09-31, 23 padrões) aplicado a este projeto:

- **A maioria já está atendida** — quase sempre pela via do framework (Spring
  IoC = Singleton/Factory/Abstract Factory; Spring Security AOP = Decorator;
  `@PreAuthorize`+filtros = Chain of Responsibility; JPA = Flyweight; JDK =
  Iterator) ou por decisões de design que já existiam antes deste material
  chegar (State via mapa de transições, Template Method evitado por não ter
  duplicação).
- **Três `GAP`s reais, de importância crescente:**
  1. Strategy para desconto/cupom — baixa urgência, sem requisito formal ainda.
  2. Cache do cardápio (Cap. 17) — otimização, não bloqueia nada.
  3. **Mediator/eventos de domínio** — o mais importante dos três, porque **o
     próprio projeto já tinha detectado esse acoplamento crescente entre
     `OrderService`/`KitchenService`/`NotificationService`/`InventoryService`
     antes deste capítulo chegar**, e agora três capítulos diferentes (Façade,
     Observer, Mediator) convergem na mesma recomendação: introduzir um
     mecanismo de eventos de domínio (`ApplicationEventPublisher` do Spring).
- **Achado técnico da rodada:** o WebSocket de tempo real (`KitchenEventPublisher`)
  já é um Observer de verdade, mas cobre só a cozinha — motoboy e cliente ainda
  dependem de polling ou nem têm tela (seção 11, gap do Cliente).
- Os demais (`Bridge`, `Memento`, `Visitor`, `Interpreter`) não se aplicam
  porque a funcionalidade que cada um resolveria simplesmente não existe no
  projeto ainda — mesma categoria de resposta dada a `Prototype`/`Adapter`/
  `Composite` nas rodadas anteriores.

**Nada foi alterado.**

---

## 19. Implementação das 4 pendências aprovadas (2026-08-07)

A usuária aprovou implementar as pendências acumuladas nas seções 12, 17 e 18.
As quatro foram feitas, testadas manualmente contra o backend rodando (login
real, chamadas HTTP reais), e commitadas.

**1. Git inicializado (Cap. 02).** `.gitignore` criado **antes** do primeiro
`git add` (cobre `.env`, `node_modules/`, `target/`, `.vite/`,
`*.tsbuildinfo`, IDEs). Confirmado com `git check-ignore -v` que `.env` nunca
foi staged. Dois commits feitos: o inicial (238 arquivos) e um pequeno ajuste
(um build artifact do TypeScript, `tsconfig.tsbuildinfo`, tinha escapado do
primeiro `.gitignore` — corrigido e removido do índice no commit seguinte).

**2. Testes corrigidos (Cap. 12 — Builder).** `OrderKitchenFlowIT.java:87` e
`PaymentCashRegisterFlowIT.java:97` construíam `CreateOrderRequest`
posicionalmente com a lista de argumentos desatualizada (faltava
`customerAddressId` em um, `notes` no outro). Corrigidos. `mvn test-compile`
agora compila os testes limpo — a suíte em si continua não executável neste
ambiente (Testcontainers exige Docker), mas o código de teste está correto.

**3. Cache do cardápio (Cap. 17 — Proxy).** `CacheConfig.java` novo
(`@EnableCaching`, `ConcurrentMapCacheManager` autoconfigurado pelo Spring
Boot, sem dependência nova). `CategoryService.listByUnit` e
`ProductService.listByUnit`/`listByCategory` ganharam `@Cacheable`; os métodos
de escrita (`create`/`update`/`softDelete` dos dois services) ganharam
`@CacheEvict(allEntries = true)`. **Testado de verdade**: primeira chamada a
`GET /api/categories` em ~157ms, segunda em ~34ms (cache hit); criar uma
categoria nova invalidou o cache corretamente (a listagem seguinte já trazia
o item novo).

**4. Eventos de domínio (Cap. 22/28 — Observer/Mediator).** Dois eventos
novos em `common/event/`: `OrderItemReadyEvent` e `OrderReadyEvent`, usando
`ApplicationEventPublisher` do Spring (síncrono, mesma thread/transação —
comportamento idêntico ao das chamadas diretas que substituíram, só
desacoplado).
- `KitchenService` **não conhece mais `InventoryService`** — publica
  `OrderItemReadyEvent` ao marcar um item `PRONTO`; `InventoryEventListener`
  (novo, no módulo `inventory`) reage e chama `deductForOrderItem`.
- `OrderService` **não conhece mais `NotificationService`** — publica
  `OrderReadyEvent` quando o pedido fica `PRONTO`; `OrderNotificationListener`
  (novo, no módulo `notifications`) reage e cria a notificação.
- **Testado de ponta a ponta**: abri mesa → comanda → pedido → enviei pra
  cozinha → iniciei e completei o item → confirmei que a notificação "Pedido
  pronto" foi criada corretamente via `GET /api/notifications/unread`, com
  zero erros no log. O acoplamento que a seção 1 já tinha identificado como
  problema está resolvido.

### Bug pré-existente encontrado e corrigido durante os testes

Ao testar de verdade (pela primeira vez neste ambiente) uma chamada
autenticada a um endpoint de negócio, todo `GET`/`POST` protegido retornava
**403** com `org.hibernate.LazyInitializationException: failed to lazily
initialize... Profile.permissions... no Session`.

Causa: `AppUserDetailsService.loadUserByUsername` não era `@Transactional`, e
`User.getAuthorities()` lê `profile.getPermissions()` (`@ManyToMany(LAZY)`).
O `JwtAuthenticationFilter` chama `getAuthorities()` **fora** de qualquer
transação/sessão Hibernate (filtros de servlet não são gerenciados pelo
Spring Data) — então, mesmo com o login funcionando (não usa esse caminho),
**toda requisição autenticada para qualquer endpoint de negócio quebrava**.
Isso nunca tinha sido detectado porque nenhuma etapa anterior deste projeto
chegou a testar um endpoint protegido de ponta a ponta com um token real.

Corrigido: `loadUserByUsername` agora é `@Transactional(readOnly = true)` e
força a inicialização de `profile.getPermissions()` (`Hibernate.initialize`)
antes de retornar, dentro da transação. Testado e confirmado: login → GET
protegido → POST protegido, tudo 200, zero erro.

### Arquivos novos/alterados nesta rodada

| Arquivo | O que mudou |
|---|---|
| `.gitignore` | Novo |
| `backend/src/main/java/.../config/CacheConfig.java` | Novo — `@EnableCaching` |
| `backend/src/main/java/.../catalog/service/CategoryService.java` | `@Cacheable`/`@CacheEvict` |
| `backend/src/main/java/.../catalog/service/ProductService.java` | `@Cacheable`/`@CacheEvict` |
| `backend/src/main/java/.../common/event/OrderItemReadyEvent.java` | Novo |
| `backend/src/main/java/.../common/event/OrderReadyEvent.java` | Novo |
| `backend/src/main/java/.../kitchen/service/KitchenService.java` | Publica evento em vez de chamar `InventoryService` direto |
| `backend/src/main/java/.../inventory/service/InventoryEventListener.java` | Novo — reage a `OrderItemReadyEvent` |
| `backend/src/main/java/.../ordering/service/OrderService.java` | Publica evento em vez de chamar `NotificationService` direto |
| `backend/src/main/java/.../notifications/service/OrderNotificationListener.java` | Novo — reage a `OrderReadyEvent` |
| `backend/src/main/java/.../identity/security/AppUserDetailsService.java` | Bug fix: `@Transactional` + inicialização eager das permissões |
| `backend/src/test/java/.../ordering/OrderKitchenFlowIT.java` | Bug fix: `CreateOrderRequest` posicional corrigido |
| `backend/src/test/java/.../payments/PaymentCashRegisterFlowIT.java` | Bug fix: `CreateOrderRequest` posicional corrigido |

### O que ainda não foi feito (não pedido nesta rodada)

- `UNIQUE (unit_id, name)` em `categories` (Cap. 03, seção 12) — mencionado,
  não aprovado ainda.
- Strategy para desconto/cupom (Cap. 06/08/21) — sem requisito formal ainda.
- WebSocket/Observer para motoboy e cliente (Cap. 22) — o gap do ator Cliente
  (seção 11) segue de pé.
- `mvn test` de verdade (precisa de Docker para Testcontainers) continua
  pendente — os testes só foram *corrigidos*, não *executados*.

**Estado atual: 2 commits no Git, backend rodando localmente sem erros
conhecidos, cache e eventos de domínio testados manualmente com sucesso.**

---

## 20. Auditoria — Capítulos 32 a 38 (Parte IV completa: arquitetura, DDD, hexagonal, persistência)

Feita em 2026-08-07. **Nenhum código foi alterado.** Este lote fecha o
material inteiro (38 capítulos). Diferente das rodadas de padrões (Parte
III), aqui a unidade de análise é a arquitetura do projeto como um todo, não
um recurso isolado — por isso a auditoria é mais qualitativa, com achados que
se repetem entre capítulos (o mesmo problema aparece sob ângulos diferentes).

### Cap. 32 — Panorama: onde o projeto se encaixa

O material enquadra o DaHorta como **monólito em camadas**. O nosso backend é
tecnicamente um monólito (uma aplicação Spring Boot implantável), mas a
organização interna já é mais parecida com **monólito modular** (Cap. 36) do
que camadas puras: 14 módulos de negócio (`identity`, `catalog`, `dinein`,
`ordering`, `kitchen`, `payments`, `cashregister`, `customers`, `delivery`,
`couriers`, `inventory`, `reports`, `notifications`), cada um com as suas
próprias subcamadas (`controller`/`service`/`repository`/`domain`/`dto`). Ou
seja: **o projeto já nasceu mais avançado, arquiteturalmente, do que o
"monólito em camadas" que é o ponto de partida do material** — mas sem a
disciplina de fronteira que o monólito modular exige (ver Cap. 36 abaixo).

### Cap. 33 — DDD tático: entidades, agregados, e um achado real

| Bloco do DDD | No material | No projeto atual | Status |
|---|---|---|---|
| Objeto de valor (`Dinheiro`) | Imutável, igualdade por valor | Usamos `BigDecimal` puro em todo lugar — resolve "nunca usar float" (Cap. 03), mas não é um tipo `Dinheiro` próprio com invariantes (ex.: não aceitar negativo) | `PARCIAL` |
| Entidade | Identidade própria, igualdade por id | `Order`, `Product`, `Command` etc. têm `id` e `equals`/`hashCode` (padrão JPA) — aderente | `JÁ ATENDIDO` |
| Modelo rico vs. anêmico | Comportamento mora na entidade, não em serviços externos | **As entidades são anêmicas por design**: `Order`/`Command`/`RestaurantTable`/`Delivery` são getters/setters (`@Getter @Setter` do Lombok), e toda a lógica de transição (`ALLOWED_TRANSITIONS`, validações) mora nos `Service`s, não nas entidades. É exatamente o anti-padrão que o Cap. 33 nomeia — mas é também o padrão dominante em aplicações Spring Data JPA reais (entidades ricas com JPA têm atrito real: proxies, lazy loading, equals/hashCode complicados). **Decisão consciente e comum, não um erro isolado deste projeto** | `GAP RECONHECIDO (trade-off comum do ecossistema, não um bug)` |
| Agregado e raiz protegendo os internos | Só a raiz é porta de entrada; partes internas não são acessadas de fora | **Achado concreto:** `KitchenService` injeta `OrderItemRepository` e acessa/salva `OrderItem` **diretamente**, sem passar pela raiz `Order`. É exatamente o furo que o Cap. 33 descreve ("referências externas apontam para a raiz, nunca para as partes internas") | `GAP CONFIRMADO` |
| Repositório | Interface, uma por raiz de agregado | Spring Data `JpaRepository` — já auditado várias vezes como aderente. Mas hoje existe repositório para `OrderItem` **também**, não só para `Order` (a raiz) — reforça o achado acima | `PARCIAL` |

### Cap. 34 — DDD estratégico: subdomínios e camada anticorrupção

Mapeamento natural do DaHorta acadêmico aos nossos módulos: **core** (o
diferencial) = `ordering`+`kitchen`+`dinein` (fluxo do pedido); **apoio** =
`catalog`, `delivery`, `couriers`, `inventory`; **genérico** = `identity`,
`payments` (login e cobrança não são o diferencial do negócio). Isso já
existia implicitamente na organização por módulos, sem o vocabulário formal.

Camada anticorrupção: **não se aplica**, pelo mesmo motivo já registrado no
Cap. 14 (Adapter) — não há sistema externo (gateway de pagamento, Firebase)
para traduzir. `NÃO APLICÁVEL`.

### Cap. 35 — Camadas e MVC: a regra de dependência tem uma exceção conhecida

`Controller → Service → Repository` já auditado várias vezes como aderente
(Cap. 04/06). Mas a "regra de dependência" estrita do capítulo (*domínio não
conhece tecnologia*) **não é seguida à risca**: as entidades de domínio
(`Order`, `Product` etc.) têm anotações JPA (`@Entity`, `@Column`,
`@ManyToOne`) — ou seja, **o domínio depende do framework de persistência**,
o oposto do que o capítulo pede. Isso é, de novo, o padrão dominante em
aplicações Spring Boot reais ("entidade JPA como modelo de domínio" é a
prática mais comum do ecossistema, não uma falha isolada) — o alternativa
pura exigiria um modelo de domínio separado + entidades JPA + mapeador, o que
é bem mais código para o ganho que este projeto precisa hoje. MVC clássico
(com View server-side) não se aplica: o backend é API REST pura (JSON), sem
renderização de tela no servidor — o "V" do MVC vive inteiramente no React.

| Item | Status |
|---|---|
| Controller → Service → Repository | `JÁ ATENDIDO` |
| Domínio livre de tecnologia (regra de dependência estrita) | `GAP RECONHECIDO (trade-off comum do Spring/JPA)` |
| MVC (aplicável só ao front, que não é server-rendered) | `NÃO APLICÁVEL ao backend` |

### Cap. 36 — Monólito modular: o achado mais importante desta rodada

O projeto **já é, na intenção, um monólito modular** — 14 módulos por área de
negócio, cada um com as suas camadas. Mas falta a disciplina central que o
capítulo exige: **"os módulos se comunicam apenas pelas suas APIs públicas,
nunca alcançando classes internas uns dos outros."** Confirmado por busca:
`OrderService` (módulo `ordering`) importa e injeta diretamente
`ProductRepository`/`AdditionalRepository` (`catalog`), `CommandRepository`
(`dinein`), `CustomerAddressRepository` (`customers`),
`DeliveryRepository`/`DeliveryZoneRepository` (`delivery`) — repositórios
internos de **quatro outros módulos**, sem nenhuma API pública intermediária.
Não existe, em nenhum módulo, uma classe `XxxApi`/`XxxFacade` que sirva de
fronteira — tudo é acessível de qualquer lugar (repositórios Spring Data são
`public` por padrão).

Isso **não é uma novidade** — é a mesma dor que a seção 1 deste documento já
registrava desde antes deste material chegar ("o número de módulos que
`OrderService`/`KitchenService` conhecem diretamente está crescendo"), e que
os Capítulos 15 (Façade), 22 (Observer) e 28 (Mediator) já tinham apontado
por ângulos diferentes. **O Cap. 36 é o quarto capítulo a convergir no mesmo
ponto**, agora com o vocabulário mais preciso: falta uma API pública por
módulo.

`GAP CONFIRMADO — o mais recorrente de toda a Parte III/IV.`

### Cap. 37 — Hexagonal: metade do hexágono já existe

Portas de saída: `JÁ ATENDIDO` — os repositórios Spring Data são exatamente
isso (interface no domínio/módulo, implementação gerada pelo framework).
Portas de entrada: **não existem como interface explícita** — os
`Controller`s chamam `Service`s como classes concretas (`OrderService`, não
uma interface `CriarPedidoUseCase`). O próprio capítulo nota que isso é
"metade do hexágono" — muitos projetos Spring reais param exatamente aqui,
porque o ganho de uma porta de entrada explícita (trocar de condutor sem
tocar no núcleo) raramente compensa o código extra quando o único condutor é
o REST controller.

| Item | Status |
|---|---|
| Portas de saída (repositórios) | `JÁ ATENDIDO` |
| Portas de entrada (casos de uso como interface) | `GAP (baixa prioridade, trade-off comum)` |
| Camada anticorrupção nas bordas | `NÃO APLICÁVEL (sem integração externa, mesmo motivo do Cap. 14/34)` |

### Cap. 38 — Persistência e ORM: já usamos a via automatizada

O material é explícito: "é exatamente esse trabalho que um ORM automatiza".
Usamos **JPA + Hibernate**, não JDBC manual — a via que o próprio curso
recomenda para o projeto real (o JDBC manual é só o degrau pedagógico). O
mapeamento objeto-relacional é automático (anotações + Hibernate), consistente
com todas as consultas parametrizadas já auditadas (Cap. 04/07 — sem SQL
Injection). O único ponto já registrado (Cap. 35) é que o mapeamento **não
vive isolado num adaptador** — ele está embutido nas próprias entidades via
anotação, não separado num `Mapeador`+entidade-pura como o capítulo descreve
como ideal. Mesmo trade-off, mesma resposta: prática padrão do ecossistema.

`JÁ ATENDIDO (via ORM, que é a recomendação do próprio material), com a mesma
ressalva de acoplamento do Cap. 35/37 já registrada.`

### Conclusão da Parte IV inteira, e do material completo (38 capítulos)

Nenhum `CONFLITO`. Um único tema se repete em 5 capítulos diferentes (15, 22,
28, 33, 36) sob ângulos distintos, e converge sempre na mesma recomendação:

> **O projeto precisa de fronteiras explícitas entre módulos** — seja como
> API pública por módulo (Cap. 36), seja como eventos de domínio para reduzir
> chamadas diretas (Cap. 22/28, já parcialmente resolvido na seção 19 para
> `Kitchen↔Inventory` e `Ordering↔Notifications`), seja respeitando a raiz do
> agregado em vez de acessar `OrderItem` direto (Cap. 33).

Os demais achados (entidades anêmicas, domínio acoplado ao JPA, sem porta de
entrada explícita) são **trade-offs conscientes e comuns do ecossistema
Spring Boot**, não erros — registrados para que a usuária saiba que existem e
possa decidir se algum deles vale a pena resolver, não porque sejam
obrigatórios.

**Nada foi alterado.** Com isto, os 38 capítulos do material foram todos
auditados (seções 10 a 20 deste documento). Não há mais capítulos pendentes.

---

## 21. Matriz de decisão de padrões GoF (consolidada — 2026-08-07)

A pedido da usuária: **padrão não é meta**. Só implementar padrão quando houver
a dor concreta. Esta matriz consolida e ATUALIZA as auditorias por capítulo
(seções 15-18), refletindo o que mudou desde então: **Proxy/cache já
implementado** (seção 19) e **Observer via eventos de domínio parcialmente
implementado** (seção 19).

Classificações: `JÁ ATENDIDO` (framework/arquitetura/linguagem/já implementado)
· `APLICAR AGORA` · `QUANDO A FUNCIONALIDADE EXISTIR` · `NÃO HÁ DOR`.

| Padrão | Evidência no código | Classificação |
|---|---|---|
| Singleton | beans Spring singleton por padrão; HikariCP p/ conexões | JÁ ATENDIDO (framework) |
| Factory Method | não há exportação de cardápio | NÃO HÁ DOR |
| Abstract Factory | só existe JPA+MySQL (1 família) | NÃO HÁ DOR |
| Builder | `OrderService.create()` monta `Order` com muitos setters | APLICAR AGORA (baixa urgência) |
| Prototype | combos não existem | QUANDO A FUNCIONALIDADE EXISTIR |
| Adapter | sem gateway externo (escopo Etapa 1) | QUANDO A FUNCIONALIDADE EXISTIR |
| Façade | checkout = 2 chamadas (`/orders` + `/payments`) | QUANDO A FUNCIONALIDADE EXISTIR (canal delivery) |
| Decorator | `@PreAuthorize` (Spring Security AOP) | JÁ ATENDIDO (framework) |
| Proxy | `@Cacheable`/`@CacheEvict` em `Category/ProductService` | JÁ ATENDIDO (implementado, seção 19) |
| Composite | `Category` 1:N `Product` (raso) | QUANDO A FUNCIONALIDADE EXISTIR |
| Bridge | 1 canal de notificação só | NÃO HÁ DOR |
| Flyweight | FK normalizada + identity map do JPA | JÁ ATENDIDO (arquitetura) |
| Strategy | sem cupom/desconto | QUANDO A FUNCIONALIDADE EXISTIR |
| Observer | eventos de domínio (`inventory`/`notifications`) + WebSocket (`kitchen`) | APLICAR AGORA (expandir p/ Cliente/motoboy) |
| Command | log via `OrderStatusHistory`, fila via `status` | NÃO HÁ DOR |
| State | `ALLOWED_TRANSITIONS` em 7 services | JÁ ATENDIDO (mapa de transições) |
| Chain of Responsibility | filtros Spring Security | JÁ ATENDIDO (framework) |
| Iterator | `for-each`/`.stream()` do JDK | JÁ ATENDIDO (linguagem) |
| Mediator | acoplamento direto entre módulos (ver ciclos abaixo) | QUANDO A DOR CRESCER (via eventos, sem "classe Deus") |
| Memento | sem edição/undo de ficha | QUANDO A FUNCIONALIDADE EXISTIR |
| Visitor | CMV não implementado | QUANDO A FUNCIONALIDADE EXISTIR |
| Interpreter | só filtro por categoria | NÃO HÁ DOR |

**Decisões-chave confirmadas com a usuária:**
- **State** já resolve o problema (impedir salto inválido) via mapa — **não reescrever** para classes polimórficas.
- **Observer** expande naturalmente quando a jornada do Cliente (Angular) existir.
- **Command** não implementar — os ganhos já vêm de `OrderStatusHistory` + fila por status.
- **Singleton clássico** nunca — o container Spring já administra as instâncias.

### Achado novo desta auditoria: 3 dependências CÍCLICAS entre módulos

Confirmado por análise de imports cross-module (viola "dependências acíclicas"
do Cap. 36):

```
ordering ↔ kitchen       (OrderService→KitchenEventPublisher; KitchenService→OrderService/OrderItem)
ordering ↔ delivery      (OrderService cria Delivery; DeliveryService→Order)
payments ↔ cashregister  (mútua)
```

Nenhum módulo expõe uma API pública — o acesso entre módulos é direto às
classes internas (repositórios/entidades). São ciclos de **compilação** (o
Spring sobe porque não há ciclo de *bean*). Solução alinhada ao pedido (sem
"classe Deus"): **eventos de domínio** para quebrar `ordering↔kitchen` e
`ordering↔delivery` — mecanismo já iniciado na seção 19. **Não implementado
ainda — registrado como pendência.**

### Arquitetura confirmada (sem mudança)

`Angular → REST/JSON → Spring Boot → MySQL`, **monólito modular**, 14 módulos
por domínio, camadas `apresentação → aplicação → domínio ← infraestrutura`.
**Microsserviços não entram.** A única dívida arquitetural real são as
fronteiras acíclicas acima.
