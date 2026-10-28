# Roteiro do vídeo pitch: Bueno's House (5 minutos)

Formato pedido pela disciplina: vídeo de **exatamente 5 minutos**, em duas partes.
**Parte 1 (3min30):** vender o sistema funcionando. **Parte 2 (1min30):** mostrar código e deploy.

Ritmo de referência: cerca de 140 palavras por minuto. Cada bloco traz o **tempo**, a **tela a
mostrar** e a **narração**. Antes de gravar, abra o site uma vez para "acordar" o servidor (o
plano gratuito hiberna por volta de 1 minuto) e deixe as abas prontas.

- Aplicação: https://bueno-s-house.vercel.app
- Documentação da API (Swagger): https://bueno-s-house.vercel.app/swagger-ui.html
- Repositório: https://github.com/Joaovsr98/Bueno-sHouse
- Logins: `admin@email.com` / `senha123`. Perfis de demonstração com senha `admin123`:
  `gerente@demo.local`, `cozinha@demo.local`, `caixa@demo.local`, `garcom@demo.local`,
  `motoboy@demo.local`, `cliente@demo.local`.
- No login, os botões de acesso rápido só escolhem a conta. A senha sempre é digitada.

---

## PARTE 1: o sistema funcionando (0:00 a 3:30)

### 0:00 a 0:25 · Abertura e o problema
**Tela:** abrir o site. As três bocas acendem com "Esquentando a chapa" e surge a pergunta
"O que você quer?", com "Matar minha fome" e "Acender a chapa".

> Numa dark kitchen a margem é apertada. Quem cozinha sem saber quanto custa cada prato,
> sem controlar o estoque e sem acompanhar os pedidos perde dinheiro sem perceber. O
> Bueno's House resolve isso: um sistema único, no ar, que vai do cardápio do cliente até
> a ficha técnica, o estoque e o dashboard do dono. Logo na entrada ele pergunta: o que
> você quer? Matar a fome ou acender a chapa?

### 0:25 a 1:10 · A jornada do cliente
**Tela:** "Matar minha fome" → cardápio com fotos → página do X-Bacon Duplo → quantidade e
observação → carrinho → checkout.

> Do lado do cliente, o cardápio é público e pode ser visto sem login. Cada prato tem a
> própria página, com foto, descrição, tempo de preparo e o modo de preparo. O cliente
> escolhe a quantidade, escreve uma observação, como "sem cebola", e adiciona ao carrinho.
> Para finalizar, entra na conta ou se cadastra já com o endereço. O bairro é escolhido
> entre os que a cozinha atende em São Paulo, e a taxa de entrega aparece na hora. O
> pagamento é simulado: Pix, cartão ou dinheiro com troco. E o preço nunca vem do
> navegador: o servidor sempre recalcula a partir do cardápio.

### 1:10 a 1:35 · Acompanhamento do pedido
**Tela:** "Meus pedidos" → linha do tempo do pedido com o código de entrega.

> Depois de pedir, o cliente acompanha tudo numa linha do tempo: recebido, confirmado, em
> preparo, pronto, saiu para entrega e entregue. A tela se atualiza sozinha, e só o dono do
> pedido vê o código que confirma a entrega com o motoboy.

### 1:35 a 2:15 · Operação: pedidos, confirmação e cozinha
**Tela:** voltar ao início → "Acender a chapa" → área da equipe (só perfis de funcionário) →
digitar a senha do gerente → Pedidos → confirmar → painel da Cozinha.

> A outra porta é a da equipe, separada da do cliente. Cada perfil entra com a própria
> senha e só vê o que pode usar. O pedido chega como "Recebido"; o gerente filtra, abre e
> confirma. Nesse momento acontece a parte mais importante: a baixa automática do estoque,
> calculada pela ficha técnica de cada prato, numa única transação. Se faltar ingrediente,
> o sistema recusa e diz o que falta. A cozinha inicia o preparo e marca como pronto. Se o
> pedido for cancelado, o estoque volta sozinho.

### 2:15 a 2:50 · Ficha técnica, food cost e estoque
**Tela:** Fichas técnicas (X-Bacon Duplo com food cost em vermelho) → Estoque (Bacon abaixo
do mínimo e o histórico de movimentações).

> A ficha técnica é o coração do sistema. Para cada prato cadastramos os ingredientes, a
> quantidade, o fator de correção, que é a perda no preparo, e o rendimento. O sistema
> calcula o custo e o food cost na hora, com um semáforo: verde, amarelo ou vermelho. O
> X-Bacon passou de 35% e o sistema avisa. No estoque, cada entrada, venda e perda fica
> registrada, e o saldo bate com as vendas. O bacon está abaixo do mínimo, e o alerta
> aparece.

### 2:50 a 3:30 · Compras, preços e dashboard do dia
**Tela:** Fornecedores → "Preços" (gráfico do histórico) → enviar e receber o pedido de compra
do bacon → Dashboard (indicadores de hoje e o alerta que sumiu).

> Para repor, o sistema compara os fornecedores pelo menor preço e mostra o histórico do
> que foi pago em cada compra, num gráfico com a variação. O pedido de compra do bacon já
> está em rascunho: eu envio ao fornecedor e, ao receber, a entrada no estoque e o novo
> custo são registrados sozinhos. Por fim, o dashboard mostra ao dono o dia em tempo
> real: faturamento, pedidos e ticket médio de hoje, além das vendas do mês, dos pratos
> mais vendidos e do food cost médio. E o alerta do bacon já sumiu.

---

## PARTE 2: código e deploy (3:30 a 5:00)

### 3:30 a 4:05 · Arquitetura e stack
**Tela:** README no GitHub → pastas dos módulos do backend.

> Por baixo, a stack é Angular 20 no frontend, Spring Boot 3 com Java 21 no backend e MySQL
> 8 no banco. O backend é um monólito modular: pedidos, cozinha, estoque, fornecedores e
> relatórios são módulos com Controller, Service e Repository. A regra de negócio fica no
> Service, a API só expõe DTOs, e um teste de arquitetura impede dependências circulares
> entre os módulos.

### 4:05 a 4:35 · Código que vale mostrar
**Tela:** `OrderService` (confirmação com baixa de estoque) → `InventoryService` →
`SecurityConfig` e o token no Swagger.

> Este método confirma o pedido e faz a baixa de estoque na mesma transação: se algo
> falhar, nada é gravado. A segurança usa JWT com o perfil do usuário dentro do token,
> senhas com BCrypt e permissões por perfil no servidor e no Angular. O banco é versionado
> com Flyway, e os testes de integração rodam com MySQL real no GitHub Actions a cada push.

### 4:35 a 5:00 · Deploy
**Tela:** Vercel (deploy "Ready") → Render (serviço "Live") → Aiven (MySQL "Running") → Swagger.

> O sistema está publicado em três serviços na nuvem: o Angular na Vercel, a API em Docker
> no Render e o banco MySQL na Aiven. Os segredos ficam em variáveis de ambiente, nunca no
> código, e cada push no GitHub publica sozinho. A documentação da API está aberta no
> Swagger, no próprio endereço do site. Obrigado!

---

## Checklist antes de gravar
- [ ] Abrir o site e o Swagger 2 minutos antes, para acordar o Render e a Aiven.
- [ ] Ter um pedido de cliente já criado para mostrar a confirmação.
- [ ] Conferir que o pedido de compra do bacon ainda está em rascunho (ele é recebido no vídeo).
- [ ] Cronometrar: a Parte 1 termina em 3:30 e a Parte 2 em 5:00.
- [ ] Publicar no YouTube (pode ser não listado) e enviar repositório e vídeo individualmente.
