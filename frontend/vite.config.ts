import { defineConfig } from 'vite';
export default defineConfig({ cacheDir: process.env.MARAMIGO_VITE_CACHE || 'node_modules/.vite', server: { proxy: { '/api': process.env.MARAMIGO_API_TARGET || 'http://127.0.0.1:8080' } } });
