import { execSync } from 'node:child_process'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Mirror the Android app's git-derived versioning: the latest tag (or short SHA)
// is baked into the bundle and shown on the Settings screen.
function gitVersion(): string {
  try {
    return execSync('git describe --tags --always', { stdio: ['ignore', 'pipe', 'ignore'] })
      .toString()
      .trim()
  } catch {
    return 'dev'
  }
}

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(gitVersion()),
  },
})
