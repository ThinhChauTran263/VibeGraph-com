import { fileURLToPath, URL } from 'node:url'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export function createViteConfig(mode: string) {
  const envDir = resolve(fileURLToPath(new URL('.', import.meta.url)), '..')
  const envPath = resolve(envDir, '.env')
  const isDockerBuild = process.env.VIBEGRAPH_DOCKER_BUILD === 'true'
  const isCi = process.env.CI === 'true'
  const ciDefaults: Record<string, string> = {
    VITE_API_URL: 'http://localhost:8080',
    VITE_WS_URL: 'http://localhost:8080/ws/graph-updates',
  }

  if (!existsSync(envPath) && !isDockerBuild && !isCi) {
    throw new Error('Missing root .env file. Copy .env.example to .env and configure it before running VibeGraph.')
  }

  const env = loadEnv(mode, envDir, '')
  const enableVueDevTools = (env.VITE_ENABLE_VUE_DEVTOOLS ?? process.env.VITE_ENABLE_VUE_DEVTOOLS) === 'true'
  for (const key of ['VITE_API_URL', 'VITE_WS_URL']) {
    if (!env[key] && !process.env[key] && isCi) {
      process.env[key] = ciDefaults[key]
    }

    if (!env[key] && !process.env[key]) {
      throw new Error(`Missing ${key} in root .env.`)
    }
  }

  return defineConfig({
    envDir,
    plugins: [
      vue(),
      enableVueDevTools ? vueDevTools() : null,
    ],
    // F-M5: split the heavy vendor libs into their own long-lived chunks so route
    // chunks stay small and browser caching survives app-code changes. The graph
    // stack (sigma/graphology) only loads with the graph route; charts load where
    // ECharts is used.
    build: {
      rollupOptions: {
        output: {
          manualChunks(id: string): string | undefined {
            if (!id.includes('node_modules')) return undefined
            // Match on the package directory, not a bare substring of the whole path:
            // `id.includes('sigma')` also matched unrelated packages whose path happens to
            // contain the word, and `id.includes('echarts')` swallowed `vue-echarts`.
            if (/node_modules\/(sigma|graphology)/.test(id)) return 'vendor-graph'
            // ECharts is deliberately NOT given a manual chunk. It is imported only by the
            // lazy admin DashboardView, so leaving it alone lets it ride that route's chunk.
            // Naming it here promoted it into a shared chunk that the entry imported
            // statically, which made dist/index.html modulepreload 671 kB of charts on the
            // landing page — the opposite of what H12 set out to do.
            return undefined
          },
        },
      },
    },
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
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  return createViteConfig(mode)
})
