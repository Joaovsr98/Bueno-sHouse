# Como testar (Fases 1–7)

Pré-requisitos: Docker Desktop (ou JDK 21 + Maven + Node 20 + MySQL 8).

## 1. Compilar e rodar testes automatizados
```bash
cd backend && mvn -Dmaven.test.skip=true compile   # só compila
mvn test                                           # precisa de Docker (Testcontainers)
cd ../frontend-angular && npm install && npm run build
```
Se algo falhar aqui, é o primeiro item a corrigir (nada disso foi compilado ainda).

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
`/cardapio` (sem login) → `/cadastro` → login → carrinho/pedidos; como admin: `/usuarios`, `/fornecedores`, `/fichas-tecnicas`, `/dashboard`.
