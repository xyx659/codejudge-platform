import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  optimizeDeps: {
    // monaco-editor 的 worker 通过 ?worker 加载原始 ESM，若被 Vite 预打包会导致主线程与
    // worker 出现两份不一致的实例，TS 语言服务收不到模型同步而报
    // "Could not find source file: 'inmemory://model/N'"。排除后保证只有一份源码实例。
    exclude: ['monaco-editor']
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
