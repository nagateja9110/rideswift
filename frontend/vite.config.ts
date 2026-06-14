import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Proxies API + WebSocket to the Spring Boot backend in dev so the SPA can use
// same-origin relative URLs (matching the production Nginx setup).
export default defineConfig({
  plugins: [react()],
  // sockjs-client references the Node `global`; map it to the browser globalThis.
  define: { global: 'globalThis' },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.BACKEND_URL || 'http://localhost:8090', changeOrigin: true },
      '/ws': { target: process.env.BACKEND_URL || 'http://localhost:8090', changeOrigin: true, ws: true },
    },
  },
});
