import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client expects Node's `global` to exist; browsers only have
    // `window`. This tells Vite to substitute `global` with `window`
    // wherever it appears in bundled code.
    global: 'window',
  },
})