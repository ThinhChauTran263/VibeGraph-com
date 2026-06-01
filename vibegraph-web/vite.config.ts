import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  // `sockjs-client` (used by the STOMP WebSocket transport) is a CommonJS lib
  // that references the Node-style `global`. In the browser there is no
  // `global`, only `globalThis`, so dev (and prod) builds throw
  // "global is not defined" when the module is evaluated. Aliasing `global`
  // to `globalThis` is the standard Vite fix and is browser-safe.
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
