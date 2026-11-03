import { defineConfig, devices } from '@playwright/test';

/**
 * Teste ponta a ponta contra o sistema de verdade: servidor Spring Boot (profile dev, com os
 * dados de demonstracao) em http://localhost:8080 e o site pelo `ng serve`, que repassa
 * /api para o servidor (proxy.conf.json). No CI, o workflow e2e.yml sobe MySQL e servidor.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  // Sem repetir: o pedido e a entrega da primeira tentativa ficariam no banco e confundiriam a segunda.
  retries: 0,
  reporter: process.env.CI ? [['github'], ['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // Um aparelho por vez: todos usam o mesmo banco e a fila do motoboy anda por ordem de chegada.
  workers: 1,
  projects: [
    { name: 'computador', use: { ...devices['Desktop Chrome'] } },
    { name: 'android', use: { ...devices['Pixel 7'] } },
    { name: 'iphone', use: { ...devices['iPhone 14'] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npx ng serve --port 4200',
        cwd: '../frontend-angular',
        url: 'http://localhost:4200',
        timeout: 300_000,
        reuseExistingServer: !process.env.CI,
      },
});
