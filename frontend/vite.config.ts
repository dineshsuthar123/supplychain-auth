import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { gzipSync } from 'node:zlib';
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'lotline-compressed-preview',
      configurePreviewServer(server) {
        const cache = new Map<string, Buffer>();
        server.middlewares.use((request, response, next) => {
          const resource = (request.url ?? '').split('?')[0];
          if (
            !/^\/assets\/[A-Za-z0-9_.-]+\.(js|css)$/.test(resource) ||
            !request.headers['accept-encoding']?.includes('gzip')
          )
            return next();
          const file = resolve('dist', '.' + resource);
          if (!existsSync(file)) return next();
          let data = cache.get(file);
          if (!data) {
            data = gzipSync(readFileSync(file));
            cache.set(file, data);
          }
          response.setHeader(
            'Content-Type',
            resource.endsWith('.css')
              ? 'text/css; charset=utf-8'
              : 'text/javascript; charset=utf-8',
          );
          response.setHeader('Content-Encoding', 'gzip');
          response.setHeader('Vary', 'Accept-Encoding');
          response.end(data);
        });
      },
    },
  ],
  server: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } } },
  preview: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } } },
  build: { chunkSizeWarningLimit: 600 },
});
