import { expect, test } from '@playwright/test';

/**
 * Telas da equipe no celular: cada uma abre sem erro e sem rolagem para o lado (conteudo
 * cortado ou largo demais). Os prints ficam no relatorio do CI para conferir o visual.
 */
const TELAS = ['/pedidos', '/mesas', '/cozinha', '/caixa', '/catalogo', '/motoboy', '/estoque',
  '/fornecedores', '/fichas-tecnicas', '/dashboard', '/usuarios'];

test('telas da equipe cabem na largura do aparelho', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'computador', 'so no celular');
  await page.goto('/login?lado=equipe');
  await page.locator('#email').fill('admin@demo.local');
  await page.locator('#password').fill('admin123');
  await page.locator('form button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login/);

  const problemas: string[] = [];
  for (const tela of TELAS) {
    await page.goto(tela);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800); // animacoes de entrada
    const largura = await page.evaluate(() => ({ pagina: document.documentElement.scrollWidth, tela: window.innerWidth }));
    if (largura.pagina > largura.tela + 1) problemas.push(`${tela}: ${largura.pagina}px numa tela de ${largura.tela}px`);
    if (await page.locator('.alert-error').first().isVisible()) problemas.push(`${tela}: ${await page.locator('.alert-error').first().textContent()}`);
    await testInfo.attach(`${testInfo.project.name}${tela.replace('/', '-')}`, {
      // Primeira tela do aparelho (o print da pagina inteira derruba o Safari do teste)
      body: await page.screenshot(), contentType: 'image/png',
    });
  }
  expect(problemas, 'telas com rolagem lateral ou erro').toEqual([]);
});
