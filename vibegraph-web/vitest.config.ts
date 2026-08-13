import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import { createViteConfig } from './vite.config'

export default mergeConfig(
  createViteConfig('test'),
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      setupFiles: ['./src/test/setup.ts'],
      // 6-F0: coverage baseline for the large-file refactor gate (EXEC-2 §2.3).
      // json-summary is machine-readable so baselines can be recorded, not eyeballed.
      coverage: {
        provider: 'v8',
        reporter: ['text', 'json-summary'],
      },
    },
  }),
)
