// 应用入口：创建 Vue 实例，注册路由后挂载到 index.html 的 #app
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
