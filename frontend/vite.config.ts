import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// O back-end responde em :8080. No desenvolvimento o Vite repassa /api para ele, então o navegador
// só fala com a própria origem (sem CORS). Em produção, VITE_API_BASE_URL aponta para a API.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // O fetch do Node exige URL absoluta; o MSW intercepta este endereço nos testes.
    env: { VITE_API_BASE_URL: 'http://localhost:8080/api/v1' },
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/vite-env.d.ts', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      thresholds: { lines: 70, statements: 70, functions: 70, branches: 70 },
    },
  },
})
