import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/ATELIQ/',
  plugins: [react()],
  // server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/media': 'http://localhost:8080', '/ws': { target: 'ws://localhost:8080', ws: true } } },
  server: { port: 5173, proxy: { '/api': 'https://ateliq-2.onrender.com', '/media': 'https://ateliq-2.onrender.com', '/ws': { target: 'ws://https://ateliq-2.onrender.com', ws: true } } },  
})
