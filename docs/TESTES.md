# Como testar — Bueno's House (Angular + Spring Boot)

## Demonstração pública

- **Aplicação:** https://buenoshouse.vnbueno.com.br
- **Saúde da API:** https://buenoshouse.vnbueno.com.br/api/health — `UP` confirmado em 21/11/2026.
- **Perfil do servidor publicado:** `dev`, com dados e contas **fictícios**. Não registrar clientes ou operações comerciais reais.

O roteiro abaixo é voltado ao ambiente de **desenvolvimento/testes**, não a operações de produção. Não execute comandos de reset destrutivo em banco de dados com informações reais.

## Situação dos testes automatizados (18/11/2026)

- Backend: integração Java/JUnit 5 + Testcontainers; rodar `cd backend && mvn verify`.
- Frontend: testes Angular + build; conferir `.github/workflows/frontend-tests.yml`.
- E2E: Playwright na `.github/workflows/e2e.yml`, com cenários de fluxo de pedido e largura das telas.
- **Última execução E2E em `main` consultada:** [GitHub Actions #44368427958](https://github.com/Joaovsr98/Bueno-sHouse/actions/runs/44368427958), commit `1330901`. Resultado: 4 aprovados, 1 ignorado e 1 falha por **timeout (180s)** no cenário de telas da equipe em iPhone. O fluxo de delivery passou em desktop, Android e iPhone. Isso exige investigação/novo teste; não permite concluir que o layout está quebrado.
- O teste de arquitetura `ModuleArchitectureTest` usa ArchUnit para bloquear ciclos entre módulos.
- A chave `Idempotency-Key` para criação de pedidos e o agrupamento em `America/Sao_Paulo` já estão no código. Acrescentar/verificar casos de reenvio concorrente e virada de dia no horário de Brasília.

---

## Roteiro local

Pré-requisitos: Docker Desktop (ou JDK 21 + Maven + Node 20 + MySQL 8).

## 1. Compilar e rodar testes automatizados
```bash
cd backend && mvn -Dmaven.test.skip=true compile   # só compila
mvn verify                                         # testes de integracao, precisa de Docker (Testcontainers)
cd ../frontend-angular && npm install && npm run build
```
A mesma suíte roda no GitHub Actions a cada push do backend.

## 2. Subir tudo
```bash
cp .env.example .env    # preencha senhas e JWT_SECRET
docker compose up -d --build
```
- Angular: http://localhost:4200 · API: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html
- Login admin de dev: `admin@demo.local` / `admin123`

## 3. Roteiro por fase (curl; troque TOKEN)
```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
H="Authorization: Bearer $TOKEN"
```
| Fase | Teste | Esperado |
|---|---|---|
| 1 | `curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"Ana","email":"ana@t.com","phone":"11999999999","password":"senha1234"}'` (2ª vez) | 1ª cria; repetir e-mail → 409 |
| 1 | `curl "localhost:8080/api/products?unitId=1"` sem token | só produtos disponíveis |
| 2 | `curl -H "$H" localhost:8080/api/inventory/recipes/1` | `costPerServing` e `foodCostPercent` (GREEN/YELLOW/RED) |
| 3 | `curl -H "$H" "localhost:8080/api/suppliers?unitId=1"`; criar compra, `PATCH /api/purchases/{id}/send`, `POST /api/purchases/{id}/receive` | status RASCUNHO→ENVIADO→RECEBIDO; estoque sobe |
| 4 | Movimento `AJUSTE_PERDA` sem `reason` | 422 |
| 4 | Cancelar pedido com baixa de estoque | estoque estornado |
| 5 | `curl -H "$H" "localhost:8080/api/reports/average-food-cost?unitId=1"`; abrir `/dashboard` no Angular | número + gráficos linha/barras |
| 6 | `curl -H "$H" localhost:8080/api/users`; POST criando GERENTE; tentar criar CLIENTE | lista staff; CLIENTE rejeitado |
| 7 | `curl -i -H "$H" "localhost:8080/api/orders?unitId=1&page=0&size=5"` (idem products/suppliers/purchases/inventory/items) | header `X-Total-Count` e ≤5 itens |
| 7 | Abrir `/swagger-ui.html` | documentação carrega |

## 4. Telas Angular a percorrer
`/` (escolha cliente × restaurante) → `/app/cardapio` (sem login) → `/registrar` → login → `/app/carrinho` e `/app/pedidos`; como admin (`admin@email.com` / `senha123`): `/pedidos`, `/cozinha`, `/estoque`, `/fichas-tecnicas`, `/fornecedores`, `/dashboard`, `/usuarios`.
