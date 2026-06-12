import { defineConfig, loadEnv } from 'vite'
import uniPlugin from '@dcloudio/vite-plugin-uni'

const uni = typeof uniPlugin === 'function' ? uniPlugin : (uniPlugin as unknown as { default: typeof uniPlugin }).default

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = env.VITE_BLUENOTE_GATEWAY || 'http://127.0.0.1:8080'

  return {
    plugins: [uni()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: gateway,
          changeOrigin: true
        },
        '/ws': {
          target: gateway,
          changeOrigin: true,
          ws: true
        }
      }
    }
  }
})
