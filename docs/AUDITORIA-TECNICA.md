# Auditoria técnica · Bueno's House

28/10/2026 · código do commit `0d6ba27` · auditoria só de leitura (nenhuma correção aplicada).

## Situação após as correções (commit `567de11`)

| Item | Situação |
|---|---|
| C1 mapeamentos JPA × Flyway | **Resolvido.** 9 campos SMALLINT/TINYINT e 3 CHAR (H1 confirmada pelo CI) mapeados com `@JdbcTypeCode`; `ddl-auto=validate` religado em produção. |
| C2 suíte de testes | **Resolvido.** Faltava o `maven-failsafe-plugin` (os `*IT` nunca rodavam). GitHub Actions roda `mvn verify` com MySQL real: **21 testes passando**. Dois testes antigos corrigidos (taxa do seed e aceite de entrega pelo perfil MOTOBOY). |
| C3 item indisponível sem estorno | **Resolvido.** `OrderItemCancelledEvent` estorna o estoque; o item sai do total do pedido. Teste: `StockConsistencyIT`. |
| C4 atualização perdida de saldo | **Resolvido.** Todo ajuste de saldo é um `UPDATE` atômico no banco. |
| C5 saldo negativo | **Resolvido.** A baixa só ocorre se houver saldo (`UPDATE ... WHERE quantidade >= baixa`); sem saldo, 422 e a confirmação é desfeita. Teste: `StockConsistencyIT`. |
| C6 idempotência da baixa | **Resolvido.** Índice único `V18` em `stock_movements`; violação vira 409. |
| C7 ciclos entre módulos | **Pendente.** Plano incremental mantido (eventos + interfaces de consulta + ArchUnit). |
| C8 contas de demonstração | **Resolvido.** Senha, perfil e situação das contas `@demo.local` e `admin@email.com` não podem ser alterados (422). Teste: `AuthorizationIT`. |
| C9, C11 dados da equipe | **Resolvido.** Entregadores, mesas, atendimentos, comandas e setores só para perfis da equipe. Teste: `AuthorizationIT`. |
| C10 notificação de outro usuário | **Resolvido.** Só o destinatário marca como lida (404 para os demais). |
| H1 colunas CHAR | **Confirmada e resolvida** (ver C1). |
| H2 colunas BOOLEAN | **Descartada:** o `validate` passou no CI. |
| H3 seeds × testes | **Confirmada e resolvida** (ver C2). |
| H4, H5 | Pendentes (idempotência do `POST /orders` e fuso horário dos relatórios). |
| Documentação (seção 3) | **Atualizada:** README (estoque, timeline, testes, escopo guia × extensões), TESTES.md, DER.md e aviso de histórico no CONTINUAR-PROJETO.md. |

---

Método: leitura do código e das migrations, mapeamento de imports entre módulos, varredura
de anotações de segurança e comparação com README, DER, TESTES.md e o guia Comanda Digital.
Limitação: esta máquina não tem JDK 21 nem Docker, então nem a validação do Hibernate nem
a suíte de testes foram executadas. Onde isso importa, o item está marcado como hipótese.

---

## 1. Problemas confirmados (com evidência no código)

| # | Problema | Evidência | Severidade | Correção recomendada | Testes necessários |
|---|---|---|---|---|---|
| C1 | Validação do Hibernate desligada porque há colunas com tipo diferente do mapeado. Colunas `SMALLINT`/`TINYINT` no banco, `Integer`/`int` (que o Hibernate espera como `INTEGER`) nas entidades. | `V3__catalog.sql:31` `prep_time_minutes SMALLINT` ↔ `Product.java:46 Integer`; `V10__delivery.sql:10,44` `estimated_minutes SMALLINT` ↔ `DeliveryZone.java:37`, `Delivery.java:44`; `V6__dinein.sql:7,22` `capacity`/`party_size SMALLINT` ↔ `RestaurantTable.java:26`, `Service.java:43 int`; `V6__dinein.sql:38` `active_flag TINYINT` ↔ `Service.java:61 Integer`; `V7__cash_registers.sql:20` `open_flag TINYINT` ↔ `CashRegister.java:52 Integer`; `V8__ordering.sql:43,70` `quantity SMALLINT` ↔ `OrderItem.java:38`, `OrderItemAdditional.java:35 int`. No deploy: `render.yaml` define `SPRING_JPA_HIBERNATE_DDL_AUTO=none`. | **Crítica** | Ajustar o mapeamento sem mexer no banco: `@JdbcTypeCode(SqlTypes.SMALLINT)` / `@JdbcTypeCode(SqlTypes.TINYINT)` (ou `columnDefinition`) nos 9 campos. Depois reativar `validate` e remover a variável do `render.yaml`. Alternativa: migration nova `V18` com `ALTER ... MODIFY ... INT`. | Teste de contexto com `ddl-auto=validate` sobre um banco vazio + todas as migrations (já existe: `contextLoadsAndAllMigrationsApplySuccessfully`). |
| C2 | A suíte de testes não consegue subir: o perfil de teste usa `ddl-auto: validate`, que falha por causa de C1. A suíte nunca foi vista passando, e o build da imagem pula os testes (`-Dmaven.test.skip=true`). | `src/test/resources/application.yml:4` `ddl-auto: validate`; `CONTINUAR-PROJETO.md` registra que `mvn test` nunca foi executado; `backend/Dockerfile`. | **Crítica** | Corrigir C1 e rodar `mvn test` em uma máquina com Docker. Em seguida, criar um job de CI (GitHub Actions) que rode `mvn test` a cada push. | A própria suíte. |
| C3 | Regressão: item marcado como "indisponível" pela cozinha vira `CANCELADO`, mas o estoque já baixado na confirmação **não é estornado** (a baixa passou a ocorrer na confirmação). | `KitchenService.markUnavailable` só faz `item.setStatus("CANCELADO")` (`KitchenService.java:120`), sem `reverseDeductionForOrderItem`. Baixa na confirmação: `OrderService.sendToKitchen`. | **Alta** | Chamar `inventoryService.reverseDeductionForOrderItem(itemId, motivo)` ao marcar indisponível (ou publicar um evento `OrderItemCancelledEvent`). Recalcular o total do pedido. | Confirmar pedido → marcar item indisponível → saldo volta ao valor inicial. |
| C4 | Atualização de saldo sem controle de concorrência: dois pedidos que usam o mesmo ingrediente, confirmados ao mesmo tempo, podem gerar "atualização perdida" (lê saldo, subtrai, grava). | `InventoryItem` não tem `@Version` (as entidades com versão são `Order`, `Command`, `Delivery`, `Courier`, `User`, `CashRegister`, `RestaurantTable`, `Service`); `InventoryService.deductForOrderItem` faz `setCurrentQuantity(getCurrentQuantity().subtract(...))`. | **Alta** | `UPDATE inventory_items SET current_quantity = current_quantity - :q WHERE id = :id` (atualização atômica) ou `@Version` + nova tentativa. | Dois pedidos confirmados em paralelo (threads) com o mesmo ingrediente: saldo final = inicial − soma das baixas. |
| C5 | Estoque pode ficar negativo: o RN03 valida o estoque na **criação** do pedido, mas a baixa ocorre na **confirmação**; entre as duas, outro pedido pode consumir o saldo. | `OrderService.create` chama `missingIngredients`; `deductForOrderItem` subtrai sem verificar o saldo (`InventoryService.java:272`). | Média | Repetir a checagem do RN03 dentro de `sendToKitchen`, na mesma transação, antes de baixar. | Criar dois pedidos com estoque para um só, confirmar ambos: o segundo recebe 422. |
| C6 | Idempotência da baixa depende de "verifica e depois insere", sem restrição única no banco. | `existsByReferenceOrderItemIdAndType` seguido de `save` (`InventoryService.deductForOrderItem`); `V11__inventory.sql` não tem `UNIQUE (reference_order_item_id, type)`. Hoje a confirmação dupla é barrada pelo `@Version` de `Order`, mas a garantia não está no banco. | Média | Migration com índice único `(reference_order_item_id, type)`; tratar a violação como "já baixado". | Duas confirmações simultâneas do mesmo pedido: só uma baixa. |
| C7 | Cinco ciclos de dependência entre módulos (o projeto documentava três). | Imports cruzados: `ordering ↔ delivery`, `ordering ↔ kitchen`, `payments ↔ cashregister`, `catalog ↔ inventory`, `identity ↔ customers`. `ordering` importa 7 módulos. | Alta | Incremental: (1) eventos para `ordering → kitchen` e `ordering → delivery` (pedido criado/confirmado/pronto); (2) interfaces de consulta (`CatalogQuery`, `StockQuery`) no lugar de repositórios alheios; (3) teste de arquitetura com ArchUnit proibindo ciclos. | ArchUnit: `slices().matching("..modules.(*)..").should().beFreeOfCycles()`. |
| C8 | Ambiente de demonstração com credenciais de administrador públicas: qualquer visitante entra como admin e pode trocar senhas, desativar usuários e apagar dados. | Botões de acesso rápido com senha fixa em `login.ts` (`DEMO_PASSWORD`); logins no `README.md`; `UserController` (`PATCH /api/users/{id}` redefine senha). | Alta (para a apresentação) | Manter o login exigido pelo guia, mas: impedir que usuários `@demo.local` e `admin@email.com` tenham senha/perfil alterados ou sejam desativados; reaplicar o seed periodicamente; deixar o acesso rápido só no perfil `dev`. | Tentar redefinir a senha do admin de demonstração: 422. |
| C9 | Cliente consegue listar os entregadores com nome, telefone e placa. | `GET /api/couriers/available` sem `@PreAuthorize` (`CourierController`); `CourierResponse` expõe `phone` e `plate`. | Média | `@PreAuthorize` para `ADMINISTRADOR, GERENTE, CAIXA`. | Cliente → 403; gerente → 200. |
| C10 | Qualquer usuário marca como lida a notificação de outra pessoa. | `NotificationService.markRead(id)` não confere o dono (`NotificationService.java:45`). | Baixa | Conferir `notification.userId == usuário atual` (senão 404). | Usuário A marca notificação de B → 404. |
| C11 | Cliente lê dados do salão (mesas, atendimentos, comandas, setores). | `GET /api/tables`, `/api/services`, `/api/commands`, `/api/kitchen-sectors`, `/api/additional-groups` sem `@PreAuthorize`. | Baixa | Restringir aos perfis da equipe. | Cliente → 403 em cada endpoint. |

## 2. Hipóteses (precisam ser confirmadas executando)

| # | Hipótese | Por que suspeitar | Como confirmar |
|---|---|---|---|
| H1 | Colunas `CHAR(36)` (`public_id`) e `CHAR(2)` (`state`) mapeadas como `String` podem ser rejeitadas pelo `validate` (`char` × `varchar`). | Depende da versão do Hibernate; o 6.x costuma aceitar, mas não foi executado. | Rodar com `validate` após corrigir C1 e ler a primeira divergência apontada. |
| H2 | Colunas `BOOLEAN` (gravadas como `TINYINT(1)` no MySQL) mapeadas como `boolean` podem gerar divergência `tinyint` × `bit`. | Erro comum de `validate` no MySQL com Hibernate 6. | Mesmo procedimento de H1. |
| H3 | Os testes de integração que dependem de contagens ou valores fixos podem quebrar com os seeds V901–V903 (fichas técnicas, 1.900 pedidos de histórico). | Os seeds novos rodam também nos testes (`locations: db/migration, db/seed`). Ex.: `fullOrderFlowFromCreationToKitchenReady` agora baixa estoque na confirmação. | Rodar `mvn test` com Docker. |
| H4 | Pedido duplicado por clique duplo ou repetição de rede. | Não há chave de idempotência no `POST /api/orders`; o frontend só desabilita o botão. | Enviar o mesmo `POST` duas vezes em sequência. |
| H5 | Fuso horário: relatórios agrupam por `DATE(completed_at)` em UTC; pedidos depois das 21h de Brasília caem no dia seguinte. | `ReportService` usa as datas do banco (UTC no Aiven). | Pedido finalizado às 22h (BRT) aparece no dia seguinte do gráfico. |

## 3. Documentação desatualizada

| Arquivo | O que está errado | Situação real |
|---|---|---|
| `README.md:56` | "baixa automática de insumos ao concluir cada item na cozinha" | A baixa ocorre ao **confirmar** o pedido (`OrderService.sendToKitchen`). |
| `docs/TESTES.md` | Rotas `/cardapio` e `/cadastro`; "Fases 1–7" | Rotas são `/app/cardapio` e `/registrar`; existe página inicial `/`. |
| `docs/DER.md` | "gerado das migrations V1–V15" | Há V16 (perfis) e V17 (`inventory_items.active`). |
| `CONTINUAR-PROJETO.md` | Fala em frontend React, "só existe a tela de login", decisão de Angular pendente | Frontend é Angular; documento serve como histórico, não como estado atual. |
| `README.md` (geral) | Não separa requisitos obrigatórios do guia das extensões (salão, caixa, motoboy, multiunidade, 7 perfis) | Ver seção 5. |

## 4. Testes: o que existe e o que falta (por risco)

Existem 14 testes de integração (11 cenários): login, health, catálogo, salão, pedido→cozinha,
pagamento→caixa, delivery completo, zona/pedido mínimo, baixa de estoque, contexto+migrations.

Prioridade de novos testes:

1. **Contexto com `validate`** (já existe; só passa depois de C1).
2. **Estoque:** confirmação baixa; cancelamento estorna; item indisponível estorna (C3); RN03 na criação e na confirmação (C5); confirmações concorrentes (C4/C6).
3. **Autorização por perfil:** matriz perfil × endpoint (403 esperados); dono do pedido, cadastro, endereços e código de entrega (404 para outros clientes); 401 sem token.
4. **Relatórios (JPQL só falha em execução):** `daily-revenue`, `monthly-revenue`, `average-ticket`, `top-products` com dados conhecidos.
5. **Fluxo do guia 9.2 ponta a ponta:** RECEBIDO → CONFIRMADO → EM_PREPARO → PRONTO → SAIU_ENTREGA → FINALIZADO, com código de entrega.
6. **Arquitetura:** ArchUnit para ciclos entre módulos (C7).

## 5. Cobertura do guia Comanda Digital

| Bloco (0,1 cada) | Situação | Observação |
|---|---|---|
| 1. Cardápio e carrinho (RF-001 a 003) | Demonstrável | Filtro por categoria, detalhe do prato, observação por item. |
| 2. Jornada do cliente (RF-004 a 008) | Demonstrável | Cadastro com endereço, volta ao checkout, pagamento simulado, timeline com "Confirmado", "Meus pedidos". |
| 3. CRUD do cardápio (RF-009, 010, 014) | Demonstrável (ALTA) | RF-014 "modo de preparo" (MÉDIA) **não existe**. |
| 4. Ficha técnica e custos (RF-011 a 013) | Demonstrável | Custo, food cost e semáforo calculados no servidor. |
| 5. Pedidos e cozinha (RF-015, 016, 019, 020) | Demonstrável | Status interno `ENVIADO_PARA_COZINHA` exibido como "Confirmado". |
| 6. Pedido e estoque (RF-017, 018) | Demonstrável, com C3/C4/C5 | Baixa na confirmação e estorno no cancelamento funcionam; indisponível não estorna. |
| 7. Fornecedores e compras (RF-021 a 026) | Demonstrável (ALTA) | RF-026 "histórico de preços com gráfico" (MÉDIA) **não existe**. |
| 8. Controle de estoque (RF-027 a 033) | Demonstrável | |
| 9. Dashboard (RF-034 a 037) | Demonstrável | Vendas diárias e mensais com dados de demonstração. |
| 10. Autenticação e RBAC (RF-038 a 043) | Demonstrável | Perfis do guia: ADMIN = ADMINISTRADOR, COZINHEIRO = COZINHA (nomes diferentes, mesma função). |

Extensões além do guia (não obrigatórias): salão (mesas, atendimentos, comandas), caixa,
motoboy, multiunidade, perfis Caixa, Garçom e Motoboy.

## 6. Ordem sugerida para as correções

1. C1 + C2: mapeamentos, `validate` reativado, `mvn test` verde em máquina com Docker e CI.
2. C3, C4, C5, C6: consistência do estoque (com os testes da seção 4, item 2).
3. C8, C9, C10, C11: proteção do ambiente de demonstração e permissões.
4. Documentação (seção 3) e separação "obrigatório × extensão" no README.
5. C7: ciclos entre módulos, de forma incremental, com ArchUnit.
