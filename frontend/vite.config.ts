import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Etapa 4 - Fundacao Tecnica: configuracao minima.
// Nenhuma tela de negocio foi criada ainda - isso comeca na Etapa 5 (Catalogo).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5190,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
