<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>审计日志</h1>
        <p>查看登录与关键管理操作记录</p>
      </div>
      <button
        type="button"
        class="secondary export-button"
        :disabled="exporting"
        @click="exportLogs"
      >
        {{ exporting ? '导出中...' : '导出 CSV' }}
      </button>
    </header>

    <section class="filters">
      <input v-model.trim="filters.username" type="text" placeholder="用户名" />
      <select v-model="filters.module">
        <option value="">全部模块</option>
        <option v-for="item in moduleOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model="filters.operation">
        <option value="">全部操作</option>
        <option v-for="item in operationOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model="filters.success">
        <option value="">全部状态</option>
        <option value="true">成功</option>
        <option value="false">失败</option>
      </select>
      <label class="time-field">
        <span>开始时间</span>
        <input v-model="filters.startTime" type="datetime-local" />
      </label>
      <label class="time-field">
        <span>结束时间</span>
        <input v-model="filters.endTime" type="datetime-local" />
      </label>
      <button type="button" class="primary" @click="applyFilters">查询</button>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>操作时间</th>
            <th>账号</th>
            <th>角色</th>
            <th>模块</th>
            <th>操作</th>
            <th>请求路径</th>
            <th>IP</th>
            <th>状态</th>
            <th>耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="logs.length === 0">
            <td colspan="9" class="empty-cell">暂无审计日志</td>
          </tr>
          <template v-else>
            <tr v-for="log in logs" :key="log.id" class="log-row" @click="openDetail(log)">
              <td>{{ formatDate(log.createdAt) }}</td>
              <td>{{ log.username || '-' }}</td>
              <td>{{ log.userRole || '-' }}</td>
              <td>{{ log.module || '-' }}</td>
              <td>{{ operationText(log.operation) }}</td>
              <td class="request-uri">{{ log.requestUri || '-' }}</td>
              <td>{{ log.clientIp || '-' }}</td>
              <td>
                <span class="status-badge" :class="log.success ? 'success' : 'failed'">
                  {{ log.success ? '成功' : '失败' }}
                </span>
              </td>
              <td>{{ log.durationMs == null ? '-' : `${log.durationMs}ms` }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <div class="pagination">
      <button type="button" :disabled="page <= 0 || loading" @click="previousPage">上一页</button>
      <span class="page-input">
        第
        <input v-model="pageInput" type="number" min="1" :max="pageCount" @keyup.enter="goToPage" />
        / {{ pageCount }} 页
      </span>
      <button type="button" :disabled="page >= pageCount - 1 || loading" @click="nextPage">下一页</button>
      <span class="total">共 {{ total }} 条</span>
    </div>

    <div v-if="selectedLog" class="modal-backdrop" @click.self="closeDetail">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>日志详情</h2>
          <button type="button" class="close-button" @click="closeDetail">关闭</button>
        </header>
        <dl class="detail-grid">
          <div><dt>Trace ID</dt><dd>{{ selectedLog.traceId || '-' }}</dd></div>
          <div><dt>操作账号</dt><dd>{{ selectedLog.username || '-' }}</dd></div>
          <div><dt>操作角色</dt><dd>{{ selectedLog.userRole || '-' }}</dd></div>
          <div><dt>客户端 IP</dt><dd>{{ selectedLog.clientIp || '-' }}</dd></div>
          <div><dt>请求方式</dt><dd>{{ selectedLog.httpMethod || '-' }}</dd></div>
          <div><dt>请求路径</dt><dd>{{ selectedLog.requestUri || '-' }}</dd></div>
          <div><dt>模块</dt><dd>{{ selectedLog.module || '-' }}</dd></div>
          <div><dt>操作</dt><dd>{{ operationText(selectedLog.operation) }}</dd></div>
          <div><dt>操作说明</dt><dd>{{ selectedLog.description || '-' }}</dd></div>
          <div><dt>状态</dt><dd>{{ selectedLog.success ? '成功' : '失败' }}</dd></div>
          <div><dt>HTTP 状态</dt><dd>{{ selectedLog.httpStatus ?? '-' }}</dd></div>
          <div><dt>执行耗时</dt><dd>{{ selectedLog.durationMs == null ? '-' : `${selectedLog.durationMs}ms` }}</dd></div>
          <div><dt>错误信息</dt><dd>{{ selectedLog.errorMessage || '-' }}</dd></div>
        </dl>
        <div class="params-block">
          <strong>请求参数</strong>
          <pre>{{ selectedLog.requestParams || '无' }}</pre>
        </div>
        <footer class="modal-footer">
          <button type="button" class="primary" @click="closeDetail">关闭</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { downloadAuditLogs, listAuditLogs } from '../../api/auditLog'

const moduleOptions = [
  { value: '认证', label: '认证' },
  { value: '用户管理', label: '用户管理' },
  { value: '系统配置', label: '系统配置' }
]

const operationOptions = [
  { value: 'LOGIN', label: '用户登录' },
  { value: 'CREATE_USER', label: '新增用户' },
  { value: 'UPDATE_USER', label: '修改用户' },
  { value: 'CHANGE_USER_ROLE', label: '修改角色' },
  { value: 'DELETE_USER', label: '删除用户' },
  { value: 'IMPORT_USERS', label: '批量导入' },
  { value: 'UPDATE_CONFIG', label: '修改系统配置' }
]

const operationTextMap = Object.fromEntries(
  operationOptions.map((item) => [item.value, item.label])
)

const filters = reactive({
  username: '',
  module: '',
  operation: '',
  success: '',
  startTime: '',
  endTime: ''
})

const logs = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const pageInput = ref('1')
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const selectedLog = ref(null)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function buildFilterParams(includePaging = true) {
  const params = {
    username: filters.username,
    module: filters.module,
    operation: filters.operation,
    success: filters.success,
    startTime: filters.startTime ? toIso(filters.startTime) : '',
    endTime: filters.endTime ? toIso(filters.endTime) : ''
  }
  if (includePaging) {
    params.page = page.value
    params.size = size.value
  }
  return params
}

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    const response = await listAuditLogs(buildFilterParams())
    logs.value = response.data.list || []
    total.value = response.data.total || 0
    pageInput.value = String(page.value + 1)
  } catch (e) {
    error.value = e.message || '审计日志加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  pageInput.value = '1'
  loadLogs()
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
    loadLogs()
  }
}

function nextPage() {
  if (page.value < pageCount.value - 1) {
    page.value += 1
    loadLogs()
  }
}

function goToPage() {
  const target = Number(pageInput.value)
  if (!Number.isInteger(target) || target < 1 || target > pageCount.value) {
    pageInput.value = String(page.value + 1)
    return
  }
  page.value = target - 1
  loadLogs()
}

async function exportLogs() {
  exporting.value = true
  error.value = ''
  try {
    await downloadAuditLogs(buildFilterParams(false))
  } catch (e) {
    error.value = e.message || '审计日志导出失败'
  } finally {
    exporting.value = false
  }
}

function openDetail(log) {
  selectedLog.value = log
}

function closeDetail() {
  selectedLog.value = null
}

function operationText(operation) {
  return operationTextMap[operation] || operation || '-'
}

function toIso(value) {
  return new Date(value).toISOString()
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadLogs)
</script>

<style scoped>
.page {
  max-width: 1280px;
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

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

input,
select,
button {
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font: inherit;
}

button {
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

.time-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.time-field span {
  color: #4b5563;
  font-size: 12px;
}

.message {
  margin-bottom: 12px;
  font-size: 14px;
}

.message.error {
  color: #b91c1c;
}

.table-shell {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

table {
  width: 100%;
  min-width: 1050px;
  border-collapse: collapse;
}

th,
td {
  padding: 11px 12px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  vertical-align: middle;
}

th {
  background: #f8fafc;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
}

.log-row {
  cursor: pointer;
}

.log-row:hover {
  background: #f9fafb;
}

.request-uri {
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-badge {
  display: inline-block;
  min-width: 46px;
  padding: 3px 7px;
  border-radius: 999px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.success {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.failed {
  background: #fee2e2;
  color: #b91c1c;
}

.empty-cell {
  height: 130px;
  text-align: center;
  color: #6b7280;
}

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  color: #4b5563;
}

.page-input input {
  width: 52px;
  text-align: center;
}

.total {
  margin-left: auto;
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
  width: min(760px, 100%);
  max-height: 90vh;
  overflow: auto;
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
  margin-bottom: 16px;
}

.modal-header h2 {
  font-size: 18px;
}

.close-button {
  border: 0;
  background: transparent;
  color: #6b7280;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.detail-grid div {
  min-width: 0;
}

.detail-grid dt {
  margin-bottom: 4px;
  color: #6b7280;
  font-size: 12px;
}

.detail-grid dd {
  color: #1f2937;
  word-break: break-all;
}

.params-block {
  margin-top: 16px;
}

.params-block strong {
  display: block;
  margin-bottom: 7px;
}

.params-block pre {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
}

.modal-footer {
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 760px) {
  .page-header {
    flex-direction: column;
  }

  .filters > * {
    width: 100%;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .total {
    margin-left: 0;
  }
}
</style>
