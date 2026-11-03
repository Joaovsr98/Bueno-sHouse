import { expect, test, type Page } from '@playwright/test';

/**
 * Telas da equipe no celular: cada uma abre sem erro e sem rolagem para o lado (conteudo
 * cortado ou largo demais). Os prints ficam no relatorio do CI para conferir o visual.
 */
const TELAS = ['/pedidos', '/mesas', '/cozinha', '/caixa', '/catalogo', '/motoboy', '/estoque',
  '/fornecedores', '/fichas-tecnicas', '/dashboard', '/usuarios'];

test('telas da equipe cabem na largura do aparelho', async ({ page: primeira, context }, testInfo) => {
  test.skip(testInfo.project.name === 'computador', 'so no celular');
  let page: Page = primeira;
  await page.goto('/login?lado=equipe');
  await page.locator('#email').fill('admin@demo.local');
  await page.locator('#password').fill('admin123');
  await page.locator('form button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login/);

  const problemas: string[] = [];
  for (const tela of TELAS) {
    for (let tentativa = 1; tentativa <= 2; tentativa++) {
    try {
      await page.goto(tela);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(800); // animacoes de entrada
      const largura = await page.evaluate(() => ({ pagina: document.documentElement.scrollWidth, tela: window.innerWidth }));
      if (largura.pagina > largura.tela + 1) problemas.push(`${tela}: ${largura.pagina}px numa tela de ${largura.tela}px`);
      const erro = page.locator('.alert-error').first();
      if (await erro.isVisible()) problemas.push(`${tela}: ${await erro.textContent()}`);
      // O print e so para conferir o visual: o Safari de teste no Linux cai ao fotografar
      // telas com graficos, depois de a tela ja ter sido medida e conferida acima.
      try {
        await testInfo.attach(`${testInfo.project.name}${tela.replace('/', '-')}`, {
          body: await page.screenshot(), contentType: 'image/png',
        });
      } catch {
        page = await context.newPage();
      }
      break;
    } catch (e) {
      // O navegador do teste caiu nesta tela: tenta de novo numa aba nova (mesma sessao);
      // so reprova se cair duas vezes (o Safari de teste no Linux cai as vezes sozinho).
      page = await context.newPage();
      if (tentativa === 2) problemas.push(`${tela}: o navegador fechou duas vezes (${String(e).split('\n')[0]})`);
    }
    }
  }
  expect(problemas, 'telas com rolagem lateral, erro ou queda do navegador').toEqual([]);
});
