
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/',
  plugins: [react()],

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'https://ateliq-2.onrender.com',
        changeOrigin: true,
        secure: true,
      },
      '/media': {
        target: 'https://ateliq-2.onrender.com',
        changeOrigin: true,
        secure: true,
      },
      '/ws': {
        target: 'wss://ateliq-2.onrender.com',
        changeOrigin: true,
        ws: true,
        secure: true,
      },
    },
  },
})
