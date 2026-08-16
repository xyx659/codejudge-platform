<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>系统配置</h1>
        <p>评测参数、AI 模型与限流阈值</p>
      </div>
      <div v-if="updatedAt" class="update-meta">
        <span>最后修改：{{ updatedBy || '-' }}</span>
        <span>{{ formatDate(updatedAt) }}</span>
      </div>
    </header>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-if="success" class="message success">{{ success }}</p>

    <p v-if="loading" class="loading">加载中...</p>

    <form v-else @submit.prevent="saveConfig">
      <section class="config-section">
        <header class="section-header">
          <h2>评测参数</h2>
        </header>
        <div class="field-grid">
          <label class="field">
            <span>单次评测超时（毫秒）</span>
            <input v-model.number="form.judge.timeoutMs" type="number" min="1000" max="10000" />
          </label>
          <label class="field">
            <span>最大内存（MB）</span>
            <input v-model.number="form.judge.memoryMb" type="number" min="64" max="1024" />
          </label>
          <label class="field">
            <span>最大并发数</span>
            <input v-model.number="form.judge.maxConcurrent" type="number" min="1" max="50" />
          </label>
        </div>
      </section>

      <section class="config-section">
        <header class="section-header">
          <h2>AI 模型配置</h2>
        </header>
        <div class="field-grid">
          <label class="field">
            <span>服务商</span>
            <select v-model="form.ai.provider">
              <option v-for="item in providerOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>模型名称</span>
            <input v-model.trim="form.ai.model" type="text" maxlength="100" />
          </label>
          <label class="field wide">
            <span>Base URL</span>
            <input v-model.trim="form.ai.baseUrl" type="text" maxlength="512" />
          </label>
        </div>

        <div class="api-key-row">
          <div class="api-key-status">
            <strong>API Key</strong>
            <span>{{ hasApiKey ? maskedApiKey : '未配置' }}</span>
          </div>
          <label class="field api-key-input">
            <span>新 API Key</span>
            <input
              v-model="form.ai.apiKey"
              type="password"
              maxlength="512"
              :disabled="form.ai.clearApiKey"
              placeholder="留空表示不修改"
            />
          </label>
          <label class="clear-key">
            <input
              v-model="form.ai.clearApiKey"
              type="checkbox"
              :disabled="!hasApiKey"
              @change="handleClearToggle"
            />
            <span>清除 API Key</span>
          </label>
        </div>
      </section>

      <section class="config-section">
        <header class="section-header">
          <h2>限流阈值</h2>
          <span class="section-note">单位：次 / 分钟</span>
        </header>
        <div class="table-shell">
          <table class="limit-table">
            <thead>
              <tr>
                <th>范围</th>
                <th>登录</th>
                <th>AI 调用</th>
                <th>代码提交</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>全局</td>
                <td><input v-model.number="form.limits.loginGlobal" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.aiGlobal" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.submitGlobal" type="number" min="1" /></td>
              </tr>
              <tr>
                <td>单用户</td>
                <td><input v-model.number="form.limits.loginPerUser" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.aiPerUser" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.submitPerUser" type="number" min="1" /></td>
              </tr>
              <tr>
                <td>单 IP</td>
                <td><input v-model.number="form.limits.loginPerIp" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.aiPerIp" type="number" min="1" /></td>
                <td><input v-model.number="form.limits.submitPerIp" type="number" min="1" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <footer class="form-footer">
        <button type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
      </footer>
    </form>

    <div v-if="clearConfirmOpen" class="modal-backdrop" @click.self="cancelClearKey">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>清除 API Key</h2>
          <button type="button" class="close-button" @click="cancelClearKey">关闭</button>
        </header>
        <p>确认清除当前 AI API Key 吗？清除后 AI 评审功能将无法调用，直到重新配置 Key。</p>
        <footer class="modal-footer">
          <button type="button" class="secondary" @click="cancelClearKey">取消</button>
          <button type="button" class="danger" @click="confirmClearKey">确认清除</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getSystemConfig, updateSystemConfig } from '../../api/systemConfig'

const providerOptions = [
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'QWEN', label: '千问' },
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容接口' }
]

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const updatedBy = ref('')
const updatedAt = ref(null)
const hasApiKey = ref(false)
const maskedApiKey = ref(null)
const clearConfirmOpen = ref(false)

const form = reactive({
  judge: {
    timeoutMs: 3000,
    memoryMb: 256,
    maxConcurrent: 10
  },
  ai: {
    provider: 'DEEPSEEK',
    model: '',
    baseUrl: '',
    apiKey: '',
    clearApiKey: false
  },
  limits: {
    loginGlobal: 100,
    loginPerUser: 10,
    loginPerIp: 20,
    aiGlobal: 300,
    aiPerUser: 30,
    aiPerIp: 100,
    submitGlobal: 600,
    submitPerUser: 60,
    submitPerIp: 120
  }
})

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    const response = await getSystemConfig()
    const data = response.data
    form.judge.timeoutMs = data.judge.timeoutMs
    form.judge.memoryMb = data.judge.memoryMb
    form.judge.maxConcurrent = data.judge.maxConcurrent
    form.ai.provider = data.ai.provider
    form.ai.model = data.ai.model
    form.ai.baseUrl = data.ai.baseUrl
    form.ai.apiKey = ''
    form.ai.clearApiKey = false
    hasApiKey.value = data.ai.hasApiKey
    maskedApiKey.value = data.ai.maskedApiKey
    form.limits.loginGlobal = data.limits.loginGlobal
    form.limits.loginPerUser = data.limits.loginPerUser
    form.limits.loginPerIp = data.limits.loginPerIp
    form.limits.aiGlobal = data.limits.aiGlobal
    form.limits.aiPerUser = data.limits.aiPerUser
    form.limits.aiPerIp = data.limits.aiPerIp
    form.limits.submitGlobal = data.limits.submitGlobal
    form.limits.submitPerUser = data.limits.submitPerUser
    form.limits.submitPerIp = data.limits.submitPerIp
    updatedBy.value = data.updatedBy
    updatedAt.value = data.updatedAt
  } catch (e) {
    error.value = e.message || '系统配置加载失败'
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (form.ai.clearApiKey && form.ai.apiKey) {
    error.value = '不能同时填写新 API Key 和清除 API Key'
    return false
  }
  const numericValues = [
    form.judge.timeoutMs,
    form.judge.memoryMb,
    form.judge.maxConcurrent,
    form.limits.loginGlobal,
    form.limits.loginPerUser,
    form.limits.loginPerIp,
    form.limits.aiGlobal,
    form.limits.aiPerUser,
    form.limits.aiPerIp,
    form.limits.submitGlobal,
    form.limits.submitPerUser,
    form.limits.submitPerIp
  ]
  if (numericValues.some((value) => !Number.isInteger(value))) {
    error.value = '数字配置项必须为整数'
    return false
  }
  if (!form.ai.provider || !form.ai.model || !form.ai.baseUrl) {
    error.value = 'AI 服务商、模型和 Base URL 不能为空'
    return false
  }
  return true
}

async function saveConfig() {
  if (!validateForm()) return

  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await updateSystemConfig({
      judge: {
        timeoutMs: form.judge.timeoutMs,
        memoryMb: form.judge.memoryMb,
        maxConcurrent: form.judge.maxConcurrent
      },
      ai: {
        provider: form.ai.provider,
        model: form.ai.model,
        baseUrl: form.ai.baseUrl,
        apiKey: form.ai.apiKey || null,
        clearApiKey: form.ai.clearApiKey
      },
      limits: {
        loginGlobal: form.limits.loginGlobal,
        loginPerUser: form.limits.loginPerUser,
        loginPerIp: form.limits.loginPerIp,
        aiGlobal: form.limits.aiGlobal,
        aiPerUser: form.limits.aiPerUser,
        aiPerIp: form.limits.aiPerIp,
        submitGlobal: form.limits.submitGlobal,
        submitPerUser: form.limits.submitPerUser,
        submitPerIp: form.limits.submitPerIp
      }
    })
    success.value = '配置已保存'
    await loadConfig()
  } catch (e) {
    error.value = e.message || '配置保存失败'
  } finally {
    saving.value = false
  }
}

function handleClearToggle(event) {
  if (event.target.checked) {
    clearConfirmOpen.value = true
  }
}

function confirmClearKey() {
  form.ai.clearApiKey = true
  form.ai.apiKey = ''
  clearConfirmOpen.value = false
}

function cancelClearKey() {
  form.ai.clearApiKey = false
  clearConfirmOpen.value = false
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadConfig)
</script>

<style scoped>
.page {
  max-width: 980px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h1 {
  font-size: 24px;
  margin-bottom: 6px;
}

.page-header p {
  color: #6b7280;
  font-size: 14px;
}

.update-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #6b7280;
  font-size: 13px;
  text-align: right;
}

.message,
.loading {
  margin-bottom: 14px;
  font-size: 14px;
}

.message.error {
  color: #b91c1c;
}

.message.success {
  color: #15803d;
}

.loading {
  color: #6b7280;
}

.config-section {
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 17px;
}

.section-note {
  color: #6b7280;
  font-size: 13px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.field {
  display: block;
}

.field span {
  display: block;
  margin-bottom: 6px;
  color: #4b5563;
  font-size: 13px;
}

input,
select {
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font: inherit;
}

input:disabled {
  cursor: not-allowed;
  background: #f3f4f6;
}

.wide {
  grid-column: span 2;
}

.api-key-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  align-items: end;
  gap: 14px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.api-key-status {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 55px;
  justify-content: center;
  color: #4b5563;
  font-size: 14px;
}

.api-key-status strong {
  color: #1f2937;
}

.clear-key {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  color: #4b5563;
  font-size: 13px;
  white-space: nowrap;
}

.clear-key input {
  width: 16px;
  min-height: 16px;
  padding: 0;
}

.table-shell {
  overflow-x: auto;
}

.limit-table {
  width: 100%;
  min-width: 620px;
  border-collapse: collapse;
}

.limit-table th,
.limit-table td {
  padding: 9px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

.limit-table th {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

.limit-table td:first-child {
  width: 90px;
  color: #374151;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 20px;
}

button {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font: inherit;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.secondary {
  color: #2563eb;
  border-color: #bfdbfe;
}

.danger {
  border-color: #fca5a5;
  color: #b91c1c;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.48);
}

.modal {
  width: min(440px, 100%);
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.modal-header,
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.modal-header {
  margin-bottom: 14px;
}

.modal-header h2 {
  font-size: 18px;
}

.modal p {
  color: #4b5563;
  line-height: 1.6;
}

.modal-footer {
  justify-content: flex-end;
  margin-top: 20px;
}

.close-button {
  border: 0;
  background: transparent;
  color: #6b7280;
}

@media (max-width: 760px) {
  .page-header {
    flex-direction: column;
  }

  .update-meta {
    text-align: left;
  }

  .field-grid,
  .api-key-row {
    grid-template-columns: 1fr;
  }

  .wide {
    grid-column: auto;
  }

  .form-footer {
    justify-content: stretch;
  }

  .form-footer button {
    width: 100%;
  }
}
</style>
