# Comanda Digital — Guia Base do Projeto

> **Documento de referência** gerado a partir do SRS v3.2 (Ago/2026) — UNASP SP, Análise e Desenvolvimento de Sistemas, Prof. Thiago Silva.
> Objetivo deste arquivo: consolidar **tudo** que precisa ser entendido e construído, organizado como especificação de requisitos + roteiro de execução, para servir de referência única durante todo o desenvolvimento.

---

## 1. Visão Geral

**O que é:** um sistema web full-stack (Angular + Spring Boot + MySQL/PostgreSQL) para gerenciar uma **Dark Kitchen** — cozinha sem salão, que vende só por delivery.

**Duas frentes do sistema (praticamente dois sistemas em um):**

| Frente | Quem usa | O que faz |
|---|---|---|
| **Loja (público)** | Cliente final | Vê cardápio, monta carrinho, faz login/cadastro, finaliza pedido, acompanha status, vê histórico |
| **Painel Admin** | Admin, Gerente, Cozinheiro | Gerencia cardápio/fichas técnicas, pedidos, estoque, fornecedores, compras e dashboard |

**Por que a ficha técnica é o coração do sistema:** margem de Dark Kitchen é apertada — o sistema precisa calcular automaticamente **quanto custa cada prato** (com base nos ingredientes) e o **food cost (%)**, além de dar **baixa automática no estoque** a cada pedido confirmado.

**Entrega:** sistema **no ar** (deploy em nuvem, front + back + banco), entrega **individual** (mesmo em dupla, cada um envia seu próprio repositório + vídeo pitch de 5 min no YouTube).

---

## 2. Perfis de Usuário (Atores)

| Perfil | Quem é | Faz o quê | Como é criado |
|---|---|---|---|
| `CLIENTE` | Consumidor final | Cardápio, carrinho, pedido, status, histórico | Cadastro público (`/register`) |
| `ADMIN` | Dono da cozinha | Tudo (cardápio, fichas, fornecedores, estoque, compras, dashboard, usuários) | Seed no banco (Flyway) |
| `GERENTE` | Operações | Pedidos, estoque, fornecedores, dashboard | Criado pelo ADMIN |
| `COZINHEIRO` | Prepara os pratos | Vê pedidos pendentes, muda status (CONFIRMADO → EM_PREPARO → PRONTO) | Criado pelo ADMIN |

> Regra chave: `CLIENTE` nunca acessa o painel admin, e cada perfil admin tem permissões distintas (RBAC obrigatório com `@PreAuthorize` no back e `Guards` no Angular).

---

## 3. Stack Obrigatória (não pode trocar)

| Camada | Tecnologia | Observações |
|---|---|---|
| Back-end | **Spring Boot 3.x**, Java 17+ | Maven ou Gradle, tanto faz |
| Persistência | **Spring Data JPA + Hibernate** | Nunca `ddl-auto=create` |
| Segurança | **Spring Security + JWT** | Expiração de 8h |
| Validação | **Bean Validation** | `@NotBlank`, `@Email`, `@Min`, etc |
| Docs API | **SpringDoc OpenAPI (Swagger)** | Disponível em `/swagger-ui.html` |
| Migrations | **Flyway** | `db/migration`, scripts `V1__...`, `V2__...` |
| Front-end | **Angular 17+** | TypeScript |
| UI Kit | Angular Material, PrimeNG **ou** Bootstrap | Livre escolha |
| HTTP | `HttpClient` + Interceptor | Injeta JWT no header |
| Forms | **Reactive Forms** | `FormGroup`, `FormArray` (ficha técnica) |
| Rotas | Angular Router + `AuthGuard` + `RoleGuard` | |
| Gráficos | **Chart.js (ng2-charts)** | Dashboard |
| Banco | **MySQL 8+ ou PostgreSQL 15+** | Escolher um só, do início ao fim |

**Organização obrigatória de pacotes no back-end:**
```
controller/   → endpoints REST
service/      → lógica de negócio (regras de negócio SEMPRE aqui)
repository/   → interfaces JPA
model/entity/ → classes @Entity
dto/          → request/response (nunca retornar @Entity direto)
config/       → SecurityConfig, CorsConfig
exception/    → @ControllerAdvice
```

**Deploy:**

| Camada | Onde |
|---|---|
| Back-end | Render, Railway, Fly.io (plano gratuito) |
| Front-end | Vercel, Netlify |
| Banco | Serviço gerenciado (Railway, Render, Aiven, Neon, Clever Cloud) — **nunca** dentro do back |
| CORS | Liberar domínio do front em produção + `localhost:4200` em dev |

⚠️ Planos gratuitos hibernam — **acordar o sistema antes de gravar o vídeo e antes da apresentação**.

---

## 4. Modelo de Dados

### 4.1 Tabelas principais (Flyway, `snake_case`)

| Tabela | Colunas-chave |
|---|---|
| `usuario` | id, nome, email (UNIQUE), senha_hash, perfil (ADMIN/GERENTE/COZINHEIRO/CLIENTE), telefone, endereco, status |
| `categoria` | id, nome, descricao, ordem, status |
| `prato` | id, categoria_id FK, nome, descricao, foto_url, preco_venda, tempo_preparo_min, status (ATIVO/INATIVO/PAUSADO) |
| `ingrediente` | id, nome, sku (UNIQUE), unidade_padrao (G/ML/UN/KG/L), estoque_minimo, custo_unitario, status |
| `ficha_tecnica` | id, prato_id FK (UNIQUE), rendimento, modo_preparo |
| `ficha_tecnica_item` | id, ficha_tecnica_id FK, ingrediente_id FK, quantidade, unidade, fator_correcao (default 1.00) |
| `fornecedor` | id, razao_social, cnpj (UNIQUE), telefone, email, status |
| `fornecedor_produto` | id, fornecedor_id FK, ingrediente_id FK, preco, unidade_venda, UNIQUE(fornecedor_id, ingrediente_id) |
| `pedido_compra` | id, fornecedor_id FK, status (RASCUNHO/ENVIADO/RECEBIDO/CANCELADO), valor_total, usuario_id FK |
| `pedido_compra_item` | id, pedido_compra_id FK, ingrediente_id FK, quantidade, preco_unitario, subtotal |
| `pedido` | id, cliente_id FK, status (RECEBIDO/CONFIRMADO/EM_PREPARO/PRONTO/SAIU_ENTREGA/FINALIZADO/CANCELADO), valor_total, endereco_entrega, observacoes, motivo_cancelamento |
| `pedido_item` | id, pedido_id FK, prato_id FK, quantidade, preco_unitario, observacoes |
| `estoque_movimentacao` | id, ingrediente_id FK, tipo (ENTRADA/SAIDA/ESTORNO), quantidade, motivo (COMPRA/VENDA/DESPERDICIO/VENCIMENTO/AJUSTE/ESTORNO), lote, validade, custo_unitario, pedido_compra_id FK NULL, pedido_id FK NULL, usuario_id FK |

### 4.2 Relacionamentos-chave

- `usuario (CLIENTE)` 1:N `pedido`
- `pedido` 1:N `pedido_item` N:1 `prato`
- `prato` 1:1 `ficha_tecnica` 1:N `ficha_tecnica_item` N:1 `ingrediente`
- `pedido → CONFIRMADO` gera `estoque_movimentacao` (baixa automática via ficha técnica)
- `pedido_compra → RECEBIDO` gera `estoque_movimentacao` (entrada automática)
- `fornecedor` N:N `ingrediente` (via `fornecedor_produto`)

📌 **Entregável extra:** gerar um **DER visual** (dbdiagram.io ou DBeaver) e salvar como imagem no repositório.

### 4.3 Fórmulas de negócio (implementar no Service, não no Controller)

```
custo_prato   = SUM(quantidade × fator_correcao × custo_unitario_ingrediente) / rendimento
food_cost (%) = (custo_prato / preco_venda) × 100
```

Cor do food cost: **verde ≤30%** · **amarelo 31–35%** · **vermelho >35%**.

---

## 5. Requisitos Funcionais (RF) por Módulo

> Prioridade: **ALTA** = obrigatório para nota · **MÉDIA** = importante, não reprova.

### 5.1 Área do Cliente
| ID | Requisito | Prior. |
|---|---|---|
| RF-001 | Cardápio público (sem login), filtro por categoria | ALTA |
| RF-002 | Detalhe do prato + botão "Adicionar ao Carrinho" | ALTA |
| RF-003 | Carrinho: qtd, observações, subtotal/total, remover/alterar | ALTA |
| RF-004 | Cadastro de cliente (nome, email, senha BCrypt, telefone, endereço), email único | ALTA |
| RF-005 | Login (JWT), redireciona pro checkout se veio do carrinho | ALTA |
| RF-006 | Checkout: resumo + endereço + pagamento simulado | ALTA |
| RF-007 | Timeline de status: RECEBIDO→CONFIRMADO→EM_PREPARO→PRONTO→SAIU_ENTREGA | ALTA |
| RF-008 | Histórico "Meus Pedidos" (mais recente primeiro) | MÉDIA |

### 5.2 Cardápio & Fichas Técnicas
| ID | Requisito | Prior. |
|---|---|---|
| RF-009 | CRUD categorias | ALTA |
| RF-010 | CRUD pratos (nome, descrição, foto URL, preço, tempo preparo, categoria, status) | ALTA |
| RF-011 | Ficha técnica: ingredientes + qtd + unidade + fator de correção + rendimento | ALTA |
| RF-012 | Custo automático calculado no Service, exibido em tempo real | ALTA |
| RF-013 | Food cost (%) com semáforo de cor | ALTA |
| RF-014 | Modo de preparo (texto livre) | MÉDIA |

### 5.3 Pedidos & Cozinha
| ID | Requisito | Prior. |
|---|---|---|
| RF-015 | Pedido novo aparece no painel com status RECEBIDO | ALTA |
| RF-016 | Ciclo de status completo (+ CANCELADO) | ALTA |
| RF-017 | Baixa automática de estoque ao CONFIRMAR (`@Transactional`) | ALTA |
| RF-018 | Cancelamento com estorno de estoque + motivo obrigatório | ALTA |
| RF-019 | Lista de pedidos paginada, com filtros (data/status/canal) | ALTA |
| RF-020 | Detalhe do pedido (itens, cliente, valores, timeline) | MÉDIA |

### 5.4 Fornecedores & Compras
| ID | Requisito | Prior. |
|---|---|---|
| RF-021 | CRUD fornecedores (com validação de CNPJ) | ALTA |
| RF-022 | Catálogo por fornecedor (ingrediente + preço + unidade) | ALTA |
| RF-023 | Cotação comparativa entre fornecedores, ordenada por preço | ALTA |
| RF-024 | Pedido de compra (RASCUNHO→ENVIADO→RECEBIDO) | ALTA |
| RF-025 | Recebimento → entrada de estoque + atualização de custo | ALTA |
| RF-026 | Histórico de preços com gráfico (Chart.js) | MÉDIA |

### 5.5 Controle de Estoque
| ID | Requisito | Prior. |
|---|---|---|
| RF-027 | CRUD ingredientes | ALTA |
| RF-028 | Entrada de estoque (automática ou manual) | ALTA |
| RF-029 | Saída automática via ficha técnica | ALTA |
| RF-030 | Saída manual (perdas) com motivo obrigatório (enum) | ALTA |
| RF-031 | Saldo em tempo real (entradas − saídas) | ALTA |
| RF-032 | Alertas de estoque mínimo (badge + card) | ALTA |
| RF-033 | Log de todas as movimentações | MÉDIA |

### 5.6 Dashboard
| ID | Requisito | Prior. |
|---|---|---|
| RF-034 | KPIs do dia (faturamento, pedidos, ticket médio, food cost médio) | ALTA |
| RF-035 | Top 5 pratos mais vendidos (gráfico de barras) | ALTA |
| RF-036 | Alertas de estoque no dashboard | ALTA |
| RF-037 | Vendas por período (gráfico de linha) | MÉDIA |

### 5.7 Autenticação & Usuários
| ID | Requisito | Prior. |
|---|---|---|
| RF-038 | Login único para todos os perfis (JWT com role no payload) | ALTA |
| RF-039 | Cadastro público só cria CLIENTE | ALTA |
| RF-040 | RBAC com `@PreAuthorize` + Guards Angular | ALTA |
| RF-041 | CRUD de usuários internos (Admin cria gerente/cozinheiro) | ALTA |
| RF-042 | HttpInterceptor injeta JWT + trata 401 | ALTA |
| RF-043 | `AuthGuard` (logado?) + `RoleGuard` (permissão?) | ALTA |

---

## 6. Regras de Negócio (RN) — sempre no Service

| ID | Regra | Onde |
|---|---|---|
| RN01 | Prato só fica ATIVO se tiver ficha técnica com ≥1 ingrediente | `PratoService` |
| RN02 | Food cost >35% → aviso no response (não bloqueia) | `FichaTecnicaService` |
| RN03 | Estoque insuficiente → 422 informando o que falta | `PedidoService` |
| RN04 | Cancelamento livre antes de EM_PREPARO; depois só GERENTE/ADMIN | `PedidoService` |
| RN05 | Recebimento de compra atualiza custo unitário do ingrediente | `CompraService` |
| RN06 | Soft delete sempre (`status = INATIVO`), nunca DELETE físico | Todos |
| RN07 | Validar CNPJ (algoritmo ou lib) | `Validator` |
| RN08 | Fator de correção ≥ 1.0 (`@Min`) | DTO |
| RN09 | Cardápio público só mostra pratos ATIVO | `PratoService` |
| RN10 | Email único (409 se já existir) | `UsuarioService` |

---

## 7. Requisitos Não Funcionais (RNF)

| ID | Exigência |
|---|---|
| RNF01 | Senha com BCrypt; nunca retornar senha no JSON |
| RNF02 | JWT expira em 8h; endpoints protegidos com `@PreAuthorize` |
| RNF03 | CORS configurado (produção + `localhost:4200`) |
| RNF04 | Camadas: Controller → Service → Repository (lógica só no Service) |
| RNF05 | DTOs para request/response; nunca expor `@Entity` |
| RNF06 | `@ControllerAdvice` com erros padronizados |
| RNF07 | Flyway (nunca `ddl-auto=create`) |
| RNF08 | Paginação (`Pageable`) em TODAS as listagens |
| RNF09 | Layout responsivo (desktop + tablet) |
| RNF10 | Feedback visual: loading + toasts de sucesso/erro |
| RNF11 | Swagger funcionando publicamente em `/swagger-ui.html` |
| RNF12 | Segredos via variáveis de ambiente (nunca commitados) |
| RNF13 | README com URLs de produção + instruções de execução |

---

## 8. Endpoints da API (contrato REST)

Todos em JSON, protegidos por JWT exceto os marcados **Público**.

```
POST   /api/auth/login                          Público
POST   /api/auth/register                        Público
GET    /api/cardapio                              Público
GET    /api/cardapio/{id}                         Público

POST   /api/pedidos                               CLIENTE
GET    /api/pedidos/meus                          CLIENTE
GET    /api/pedidos/{id}/status                   CLIENTE

GET    /api/admin/pedidos                         ADM/GER/COZ
PATCH  /api/admin/pedidos/{id}/status              ADM/GER/COZ
PATCH  /api/admin/pedidos/{id}/cancelar            ADM/GER

CRUD   /api/admin/categorias                       ADM/GER
CRUD   /api/admin/pratos                           ADM/GER
GET    /api/admin/pratos/{id}/custo                ADM/GER
GET/POST/PUT /api/admin/pratos/{id}/ficha          ADM/GER
CRUD   /api/admin/ingredientes                     ADM/GER

GET    /api/admin/estoque/saldo                    ADM/GER
GET    /api/admin/estoque/alertas                  ADM/GER
POST   /api/admin/estoque/movimentacao             ADM/GER

CRUD   /api/admin/fornecedores                     ADM/GER
GET    /api/admin/cotacao/{ingredienteId}          ADM/GER
CRUD   /api/admin/compras                          ADM/GER
POST   /api/admin/compras/{id}/receber             ADM/GER

GET    /api/admin/dashboard/resumo                 ADM/GER
GET    /api/admin/dashboard/top-pratos             ADM/GER

CRUD   /api/admin/usuarios                         ADM
```

---

## 9. Fluxos do Sistema

### 9.1 Cliente faz pedido
`Cardápio (GET público) → Detalhe prato → Carrinho local → Login/Cadastro (se preciso) → Checkout (POST /api/pedidos, valida estoque) → Pedido RECEBIDO → Acompanhamento`

### 9.2 Cozinha processa pedido
`RECEBIDO → (Gerente confirma) CONFIRMADO [baixa estoque automática] → (Cozinheiro) EM_PREPARO → PRONTO → (Gerente) SAIU_ENTREGA → FINALIZADO`

### 9.3 Compras / reabastecimento
`Alerta de estoque baixo → Cotação comparativa → Pedido de compra (RASCUNHO→ENVIADO) → Recebimento (RECEBIDO) [entrada estoque + atualiza custo] → Food cost dos pratos recalculado`

---

## 10. Roteiro de Implementação — Passo a Passo

> Ordem sugerida pensando em **dependências técnicas**: primeiro o que tudo depende (auth, banco), depois o domínio central (cardápio/ficha técnica), depois o fluxo transacional (pedido/estoque), por último dashboard e deploy final.

**Passo 1 — Setup do ambiente**
- Criar projeto Spring Boot (start.spring.io: Web, JPA, Security, Validation, Flyway, driver do banco escolhido)
- Criar projeto Angular (`ng new`) e escolher UI kit
- Subir banco local (MySQL ou PostgreSQL) e configurar `application.yml` via variáveis de ambiente

**Passo 2 — Modelagem do banco (Flyway)**
- Escrever `V1__create_tables.sql` com todas as tabelas da seção 4.1
- Escrever `V2__seed_data.sql`: 1 admin (`admin@email.com` / `senha123`), categorias, ingredientes, ≥5 pratos com ficha técnica
- Gerar o DER visual e salvar no repositório

**Passo 3 — Autenticação e RBAC (base de tudo)**
- Entidade `Usuario`, enum `Perfil`
- Spring Security + JWT (login, geração/validação de token, filtro)
- `POST /api/auth/register` (só CLIENTE) e `POST /api/auth/login`
- `@PreAuthorize` por perfil nos controllers
- No Angular: serviço de auth, `HttpInterceptor` (injeta Bearer token, trata 401), `AuthGuard`, `RoleGuard`

**Passo 4 — Domínio central: cardápio e ficha técnica**
- Entidades `Categoria`, `Prato`, `Ingrediente`, `FichaTecnica`, `FichaTecnicaItem`
- CRUDs admin (categorias, pratos, ingredientes) com soft delete (RN06)
- Lógica de cálculo de custo e food cost no `FichaTecnicaService` (RF-012, RF-013, RN01, RN02, RN08)
- Telas admin: gestão de cardápio + formulário de ficha técnica (`FormArray`)

**Passo 5 — Loja pública (frontend cliente)**
- Cardápio público com filtro de categoria (RF-001, RN09)
- Detalhe do prato (RF-002)
- Carrinho (estado local no Angular) (RF-003)
- Telas de cadastro/login do cliente (RF-004, RF-005, RN10)

**Passo 6 — Pedido e fluxo transacional (o mais crítico)**
- Entidades `Pedido`, `PedidoItem`, `EstoqueMovimentacao`
- Checkout → `POST /api/pedidos`: valida estoque (RN03), cria pedido RECEBIDO
- Mudança de status com baixa automática `@Transactional` ao CONFIRMAR (RF-017)
- Cancelamento com estorno + regra de permissão por status (RF-018, RN04)
- Telas: acompanhamento com timeline (RF-007), histórico "meus pedidos" (RF-008), painel de pedidos paginado da cozinha (RF-019, RF-020)

**Passo 7 — Fornecedores e compras**
- Entidades `Fornecedor`, `FornecedorProduto`, `PedidoCompra`, `PedidoCompraItem`
- CRUD fornecedores + validação de CNPJ (RN07)
- Catálogo por fornecedor e cotação comparativa (RF-022, RF-023)
- Fluxo de pedido de compra e recebimento com atualização de custo (RF-024, RF-025, RN05)

**Passo 8 — Estoque completo**
- Saldo em tempo real (RF-031), alertas de mínimo (RF-032)
- Saída manual com motivo obrigatório (RF-030)
- Tela de movimentações (RF-033)

**Passo 9 — Dashboard**
- Queries de KPIs no repository (RF-034)
- Top 5 pratos e vendas por período com Chart.js (RF-035, RF-037)
- Card de alertas de estoque no dashboard (RF-036)

**Passo 10 — Polimento e NFRs**
- `@ControllerAdvice` global (RNF06), DTOs em todos os endpoints (RNF05)
- Paginação em todas as listagens (RNF08)
- Responsividade (RNF09), loading states e toasts (RNF10)
- Conferir Swagger completo (RNF11)

**Passo 11 — Deploy**
- Banco gerenciado em nuvem (Railway/Render/Neon/Aiven)
- Back-end em Render/Railway/Fly.io com variáveis de ambiente (RNF12)
- Front-end em Vercel/Netlify com `environment.prod.ts` apontando pro back
- CORS liberado para o domínio de produção (RNF03)
- Testar Swagger público, aquecer o sistema antes de usar

**Passo 12 — Entrega**
- README.md com URLs de produção + como rodar localmente (RNF13)
- Gravar vídeo pitch (5 min): 3min30 vendendo o sistema funcionando + 1min30 mostrando código/deploy
- Enviar (individualmente!) link do repositório + link do vídeo no YouTube

---

## 11. Critérios de Avaliação (Sprint Final = 20% da média = 2,0 pontos)

| Critério | Peso | Condição |
|---|---|---|
| Stack correta | 0,5 | Angular + Spring Boot + MySQL/PostgreSQL, senão zera |
| Sistema no ar | 0,5 | URL pública acessível no momento da avaliação, senão zera |
| Funcionalidades | 1,0 | 10 blocos de 0,1 cada, cada bloco só pontua se **todos os RF de prioridade ALTA** do bloco estiverem completos |

### Os 10 blocos de 0,1
1. Cardápio público e carrinho (RF-001 a 003)
2. Jornada do pedido do cliente (RF-004 a 008)
3. CRUD de cardápio (RF-009, 010, 014)
4. Ficha técnica e custos (RF-011 a 013)
5. Pedidos e cozinha (RF-015, 016, 019, 020)
6. Integração pedido-estoque (RF-017, 018)
7. Fornecedores e compras (RF-021 a 026)
8. Controle de estoque (RF-027 a 033)
9. Dashboard (RF-034 a 037)
10. Autenticação e RBAC (RF-038 a 043)

---

## 12. Regras Gerais de Entrega — Não Esquecer

- Entrega **individual obrigatória** (repositório + vídeo), mesmo em dupla — quem não enviar fica sem nota.
- Vídeo no YouTube (pode ser não listado), exatamente 5 minutos, com as duas partes (pitch + código/deploy).
- Seed obrigatório: `admin@email.com` / `senha123` + dados de exemplo.
- Uso de IA é permitido, **mas** é preciso saber explicar qualquer trecho do código.
- Plágio = zero para todos os envolvidos.
- Atraso: até 1 semana = −20%; depois disso, não é mais aceito.

---

## 13. Checklist Final de Conferência

- [ ] Front, back e banco publicados e acessíveis por URL pública
- [ ] Swagger acessível em `/swagger-ui.html`
- [ ] Login funcionando para os 4 perfis com JWT
- [ ] Nenhuma senha aparece em nenhum response JSON
- [ ] Todos os CRUDs com soft delete (sem DELETE físico)
- [ ] Cálculo de custo/food cost correto e em tempo real
- [ ] Baixa automática de estoque testada (pedido CONFIRMADO)
- [ ] Cancelamento com estorno testado
- [ ] Todas as listagens paginadas
- [ ] CORS liberado para o domínio de produção
- [ ] README com URLs + instruções de execução
- [ ] DER do banco anexado ao repositório
- [ ] Vídeo gravado dentro de 5 minutos, com as 2 partes
- [ ] Link do repositório e do vídeo enviados individualmente
