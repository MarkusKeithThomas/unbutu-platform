import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// `npm run dev` (http://localhost:5173): /api/** chuyển tới api-gateway local (make up) → cùng origin như prod,
// cookie refresh (Path=/api/identity) hoạt động y hệt.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
