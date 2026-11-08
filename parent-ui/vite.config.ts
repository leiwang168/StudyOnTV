import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import {fileURLToPath,URL} from 'node:url';
import {homeCss} from './home-css.mjs';
export default defineConfig({base:'./',plugins:[react(),tailwindcss()],css:{postcss:{plugins:[homeCss()]}},resolve:{alias:{'@':fileURLToPath(new URL('./src',import.meta.url))}},build:{outDir:'../admin',emptyOutDir:true,sourcemap:false,target:'es2020',rollupOptions:{output:{manualChunks:{charts:['recharts']}}}},server:{proxy:{'/api':'http://127.0.0.1:8099'}}});
