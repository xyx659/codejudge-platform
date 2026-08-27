<!-- 通用登录面板：按角色定制标题/主题色/演示账号，登录时校验角色是否匹配本端 -->
<template>
  <div class="login-page" :style="{ background: `linear-gradient(135deg, ${themeDark} 0%, ${theme} 100%)` }">
    <div class="login-card">
      <h1>{{ title }}</h1>
      <p class="subtitle">{{ subtitle }}</p>
      <form @submit.prevent="handleLogin">
        <label class="field">
          <span>{{ accountLabel }}</span>
          <input v-model.trim="username" type="text" :placeholder="`请输入${accountLabel}`" autocomplete="username" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :style="{ background: theme }" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
      <p class="hint">{{ hint }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'
import { setAuth } from '../utils/auth'

const props = defineProps({
  role: { type: String, required: true },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  homePath: { type: String, required: true },
  theme: { type: String, default: '#2563eb' },
  themeDark: { type: String, default: '#1e3a5f' },
  accountLabel: { type: String, default: '账号' },
  hint: { type: String, default: '' }
})

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  if (!username.value || !password.value) {
    error.value = `请输入${props.accountLabel}和密码`
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await login(username.value, password.value, props.role)
    const data = res.data
    setAuth(data.token, { username: data.username, name: data.name, role: data.role })
    router.replace(props.homePath)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.login-card {
  width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.login-card h1 {
  font-size: 18px;
  text-align: center;
  margin-bottom: 6px;
}

.subtitle {
  text-align: center;
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 24px;
}

.field {
  display: block;
  margin-bottom: 16px;
}

.field span {
  display: block;
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
}

.field input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.field input:focus {
  outline: none;
  border-color: #2563eb;
}

.error {
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 12px;
}

button {
  width: 100%;
  padding: 10px;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin-top: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
}

</style>
