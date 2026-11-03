import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * O caminho inteiro de um pedido de delivery, pela tela, com servidor e banco de verdade:
 * a cliente escolhe o lanche, cria a conta e finaliza o pedido; a equipe confirma; a cozinha
 * prepara; o motoboy pega a entrega na fila (endereco oculto ate aceitar), retira, sai e
 * confirma com o codigo que a cliente ve no acompanhamento; a cliente ve o pedido entregue.
 *
 * ViaCEP, OpenStreetMap e Google Maps sao simulados: o teste nao depende de servico de fora.
 */

/** Cliente novo a cada rodada e a cada aparelho (o e-mail e o telefone sao unicos). */
function novoCliente(aparelho: string) {
  const id = (Date.now() + aparelho.length).toString().slice(-8);
  return { nome: 'Cliente Ponta a Ponta', email: `e2e.${aparelho}.${id}@teste.local`, telefone: `119${id}`, senha: 'senhaE2E123' };
}

async function semServicosDeFora(page: Page): Promise<void> {
  await page.route('https://viacep.com.br/**', (route) =>
    route.fulfill({
      json: { cep: '01001-000', logradouro: 'Praça da Sé', bairro: 'Sé', localidade: 'São Paulo', uf: 'SP' },
    }),
  );
  await page.route('https://nominatim.openstreetmap.org/**', (route) => route.fulfill({ json: [] }));
  await page.route(/https:\/\/(maps\.google\.com|www\.google\.com\/maps)\//, (route) =>
    route.fulfill({ contentType: 'text/html', body: '<html><body>mapa</body></html>' }),
  );
}

/** Cada pessoa num navegador separado (sessoes independentes, como na vida real). */
async function novaPessoa(browser: Browser): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await semServicosDeFora(page);
  return page;
}

/** Espera o proximo passo aparecer; se a tela mostrar um erro, a falha diz qual foi. */
async function semErroNaTela(page: Page, proximo: ReturnType<Page['locator']>): Promise<void> {
  const erro = page.locator('.alert-error');
  await expect(proximo.or(erro).first()).toBeVisible();
  if (await erro.isVisible()) throw new Error(`A tela mostrou um erro: ${(await erro.textContent())?.trim()}`);
}

async function entrarNaEquipe(page: Page, email: string, senha: string): Promise<void> {
  await page.goto('/login?lado=equipe');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(senha);
  await page.locator('form button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login/);
}

test('pedido de delivery: do cardapio ate a entrega confirmada pelo motoboy', async ({ browser }, testInfo) => {
  const cliente = novoCliente(testInfo.project.name);
  // 1. Cliente: escolhe o lanche, cria a conta com endereco no Centro e finaliza o pedido
  const clientePage = await novaPessoa(browser);
  await clientePage.goto('/app/cardapio');
  await clientePage
    .getByRole('link', { name: /X-Bacon Duplo/ })
    .getByRole('button', { name: '+ Adicionar' })
    .click();
  await clientePage.getByRole('link', { name: /Ver carrinho/ }).click();

  await expect(clientePage).toHaveURL(/\/login/);
  await clientePage.getByRole('link', { name: /Criar conta/ }).click();
  await clientePage.locator('#fullName').fill(cliente.nome);
  await clientePage.locator('#email').fill(cliente.email);
  await clientePage.locator('#phone').fill(cliente.telefone);
  await clientePage.locator('#password').fill(cliente.senha);
  await clientePage.locator('#zipCode').fill('01001000');
  await expect(clientePage.locator('#street')).toHaveValue('Praça da Sé');
  await clientePage.locator('#neighborhood').selectOption('Centro');
  await clientePage.locator('#number').fill('100');
  await clientePage.getByRole('button', { name: /^Criar conta/ }).click();

  await expect(clientePage).toHaveURL(/\/app\/carrinho/);
  await clientePage.getByRole('button', { name: 'Finalizar pedido' }).click();
  await expect(clientePage).toHaveURL(/\/app\/pedidos\/\d+/);
  const orderId = clientePage.url().match(/pedidos\/(\d+)/)![1];
  const titulo = await clientePage.getByRole('heading', { name: /Pedido #\d+/ }).textContent();
  const numero = titulo!.match(/#(\d+)/)![1];
  const caixaDoCodigo = clientePage.locator('div.glass', { hasText: 'Código de entrega' });
  await expect(caixaDoCodigo).toBeVisible();
  const codigo = (await caixaDoCodigo.textContent())!.match(/(\d{6})/)![1];

  // 2. Equipe confirma o pedido e a cozinha prepara
  const equipe = await novaPessoa(browser);
  await entrarNaEquipe(equipe, 'admin@demo.local', 'admin123');
  await equipe.goto('/pedidos');
  await equipe.getByRole('row').filter({ hasText: `#${numero}` }).getByRole('button', { name: 'Confirmar' }).click();
  await expect(equipe.getByText(`Pedido #${numero} confirmado`)).toBeVisible();

  await equipe.goto('/cozinha');
  const tarefa = equipe.locator('div.glass').filter({ has: equipe.getByText(`Pedido #${numero}`, { exact: true }) });
  await tarefa.getByRole('button', { name: 'Iniciar preparo' }).click();
  await tarefa.getByRole('button', { name: 'Concluir' }).click();

  // 3. Motoboy: a entrega e a primeira da fila, com o endereco oculto ate aceitar
  const motoboy = await novaPessoa(browser);
  await entrarNaEquipe(motoboy, 'motoboy@demo.local', 'admin123');
  await motoboy.goto('/motoboy');
  await expect(motoboy.getByRole('heading', { name: new RegExp(`do pedido #${orderId}$`) })).toBeVisible();
  await expect(motoboy.getByText('Rua e número aparecem quando você aceitar a entrega.')).toBeVisible();

  await motoboy.getByRole('button', { name: 'Aceitar e buscar na cozinha' }).click();
  await semErroNaTela(motoboy, motoboy.getByRole('button', { name: 'Confirmar retirada no restaurante' }));
  await expect(motoboy.getByText(/Praça da Sé, 100/)).toBeVisible();
  await motoboy.getByRole('button', { name: 'Confirmar retirada no restaurante' }).click();
  await motoboy.getByRole('button', { name: 'Saí para entrega' }).click();
  await motoboy.locator('#confirmationCode').fill(codigo);
  await motoboy.locator('#receivedByName').fill(cliente.nome);
  await motoboy.getByRole('button', { name: 'Confirmar entrega' }).click();
  await expect(motoboy.getByText(/Nenhuma entrega pronta agora/)).toBeVisible();

  // 4. Cliente ve o pedido entregue
  await clientePage.reload();
  await expect(clientePage.getByText(/Entregue|Finalizado/).first()).toBeVisible();
});
