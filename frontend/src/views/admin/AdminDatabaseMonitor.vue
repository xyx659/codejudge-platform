<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>数据库监控</h1>
        <p>MySQL、MongoDB 状态与历史快照</p>
      </div>
      <div class="header-actions">
        <label class="auto-refresh">
          <input v-model="autoRefresh" type="checkbox" @change="toggleAutoRefresh" />
          <span>自动刷新</span>
        </label>
        <button type="button" class="primary" :disabled="loading" @click="loadStatus">
          {{ loading ? '刷新中...' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="message error">{{ error }}</p>

    <template v-if="status">
      <section class="db-section">
        <header class="section-header">
          <h2>MySQL</h2>
          <span class="status-badge" :class="statusClass(status.mysql.status)">
            {{ status.mysql.status === 'ok' ? '正常' : '异常' }}
          </span>
        </header>
        <div class="metrics-grid">
          <div class="metric"><span>版本</span><strong>{{ status.mysql.version || '-' }}</strong></div>
          <div class="metric"><span>运行时长</span><strong>{{ formatDuration(status.mysql.uptimeSeconds) }}</strong></div>
          <div class="metric"><span>连接数</span><strong>{{ status.mysql.currentConnections }} / {{ status.mysql.maxConnections }}</strong></div>
          <div class="metric"><span>连接使用率</span><strong :class="usageClass(status.mysql.connectionUsagePercent)">{{ status.mysql.connectionUsagePercent.toFixed(1) }}%</strong></div>
          <div class="metric"><span>数据库大小</span><strong>{{ status.mysql.databaseSizeMb.toFixed(2) }} MB</strong></div>
          <div class="metric"><span>慢查询数</span><strong>{{ status.mysql.slowQueries }}</strong></div>
          <div class="metric"><span>复制延迟</span><strong>{{ status.mysql.replicationDelayMs == null ? '-' : `${status.mysql.replicationDelayMs}ms` }}</strong></div>
          <div class="metric"><span>磁盘</span><strong>{{ diskText(status.mysql.diskFreeMb, status.mysql.diskTotalMb) }}</strong></div>
        </div>
        <p v-if="status.mysql.errorMessage" class="db-error">{{ status.mysql.errorMessage }}</p>

        <h3>关键表</h3>
        <div class="table-shell">
          <table>
            <thead><tr><th>表名</th><th>行数</th><th>数据大小</th><th>索引大小</th></tr></thead>
            <tbody>
              <tr v-for="table in status.mysql.tables || []" :key="table.tableName">
                <td>{{ table.tableName }}</td><td>{{ table.rows }}</td>
                <td>{{ table.dataLengthMb.toFixed(2) }} MB</td><td>{{ table.indexLengthMb.toFixed(2) }} MB</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h3>慢查询详情</h3>
        <div class="table-shell">
          <table>
            <thead><tr><th>耗时</th><th>锁时间</th><th>扫描行数</th><th>返回行数</th><th>SQL</th></tr></thead>
            <tbody>
              <tr v-if="(status.mysql.slowQueryDetails || []).length === 0"><td colspan="5" class="empty">暂无慢查询</td></tr>
              <tr v-for="query in status.mysql.slowQueryDetails || []" :key="`${query.sqlText}-${query.durationSeconds}`">
                <td>{{ query.durationSeconds.toFixed(2) }}s</td>
                <td>{{ query.lockTimeSeconds.toFixed(2) }}s</td>
                <td>{{ query.rowsExamined }}</td><td>{{ query.rowsSent }}</td>
                <td class="sql-cell">{{ query.sqlText }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="db-section">
        <header class="section-header">
          <h2>MongoDB</h2>
          <span class="status-badge" :class="statusClass(status.mongo.status)">
            {{ status.mongo.status === 'ok' ? '正常' : '异常' }}
          </span>
        </header>
        <div class="metrics-grid">
          <div class="metric"><span>版本</span><strong>{{ status.mongo.version || '-' }}</strong></div>
          <div class="metric"><span>运行时长</span><strong>{{ formatDuration(status.mongo.uptimeSeconds) }}</strong></div>
          <div class="metric"><span>当前连接</span><strong>{{ status.mongo.currentConnections }}</strong></div>
          <div class="metric"><span>常驻内存</span><strong>{{ status.mongo.residentMemoryMb.toFixed(2) }} MB</strong></div>
          <div class="metric"><span>数据库大小</span><strong>{{ status.mongo.databaseSizeMb.toFixed(2) }} MB</strong></div>
          <div class="metric"><span>磁盘</span><strong>{{ diskText(status.mongo.diskFreeMb, status.mongo.diskTotalMb) }}</strong></div>
        </div>
        <p v-if="status.mongo.errorMessage" class="db-error">{{ status.mongo.errorMessage }}</p>

        <h3>关键集合</h3>
        <div class="table-shell">
          <table>
            <thead><tr><th>集合名</th><th>文档数</th><th>存储大小</th></tr></thead>
            <tbody>
              <tr v-for="collection in status.mongo.collections || []" :key="collection.collectionName">
                <td>{{ collection.collectionName }}</td><td>{{ collection.count }}</td>
                <td>{{ collection.storageSizeMb.toFixed(2) }} MB</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h3>Opcounters</h3>
        <div class="metrics-grid compact">
          <div v-for="(value, key) in status.mongo.opcounters || {}" :key="key" class="metric">
            <span>{{ key }}</span><strong>{{ value }}</strong>
          </div>
        </div>
      </section>
    </template>

    <section class="db-section">
      <header class="section-header">
        <h2>历史快照</h2>
      </header>
      <div class="history-filters">
        <label>开始时间<input v-model="history.startTime" type="datetime-local" /></label>
        <label>结束时间<input v-model="history.endTime" type="datetime-local" /></label>
        <button type="button" class="primary" @click="loadHistory">查询历史</button>
      </div>
      <div class="table-shell">
        <table>
          <thead><tr><th>采集时间</th><th>MySQL</th><th>MongoDB</th><th>MySQL 连接</th><th>Mongo 连接</th></tr></thead>
          <tbody>
            <tr v-if="historyItems.length === 0"><td colspan="5" class="empty">暂无历史快照</td></tr>
            <tr v-for="item in historyItems" :key="item.id">
              <td>{{ formatDate(item.snapshot.collectedAt) }}</td>
              <td>{{ item.snapshot.mysql.status }}</td><td>{{ item.snapshot.mongo.status }}</td>
              <td>{{ item.snapshot.mysql.currentConnections }}</td><td>{{ item.snapshot.mongo.currentConnections }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <button type="button" :disabled="history.page <= 0" @click="changeHistoryPage(-1)">上一页</button>
        <span>第 {{ history.page + 1 }} / {{ historyPageCount }} 页</span>
        <button type="button" :disabled="history.page >= historyPageCount - 1" @click="changeHistoryPage(1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { getDatabaseHistory, getDatabaseStatus } from '../../api/databaseMonitor'

const status = ref(null)
const loading = ref(false)
const error = ref('')
const autoRefresh = ref(false)
let refreshTimer = null

const history = reactive({
  page: 0,
  size: 20,
  total: 0,
  startTime: '',
  endTime: ''
})
const historyItems = ref([])

const historyPageCount = computed(() => Math.max(1, Math.ceil(history.total / history.size)))

async function loadStatus() {
  loading.value = true
  error.value = ''
  try {
    const response = await getDatabaseStatus()
    status.value = response.data
  } catch (e) {
    error.value = e.message || '数据库监控状态加载失败'
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const response = await getDatabaseHistory({
      page: history.page,
      size: history.size,
      startTime: history.startTime ? new Date(history.startTime).toISOString() : '',
      endTime: history.endTime ? new Date(history.endTime).toISOString() : ''
    })
    historyItems.value = response.data.list || []
    history.total = response.data.total || 0
  } catch (e) {
    error.value = e.message || '历史快照加载失败'
  }
}

function changeHistoryPage(delta) {
  history.page += delta
  loadHistory()
}

function toggleAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (autoRefresh.value) {
    refreshTimer = setInterval(loadStatus, 30000)
  }
}

function statusClass(value) {
  return value === 'ok' ? 'ok' : 'bad'
}

function usageClass(value) {
  if (value >= 95) return 'severe'
  if (value >= 80) return 'warning'
  return ''
}

function formatDuration(seconds) {
  const value = Number(seconds || 0)
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  return `${days}天 ${hours}小时 ${minutes}分钟`
}

function diskText(freeMb, totalMb) {
  return `${Number(freeMb || 0).toFixed(0)} / ${Number(totalMb || 0).toFixed(0)} MB`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(() => {
  loadStatus()
  loadHistory()
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-header h1 { font-size: 24px; margin-bottom: 6px; }
.page-header p { color: #6b7280; font-size: 14px; }
.header-actions, .history-filters, .pagination { display: flex; align-items: center; gap: 10px; }
.auto-refresh { display: flex; align-items: center; gap: 6px; color: #4b5563; }
input, button { min-height: 36px; padding: 0 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #1f2937; font: inherit; }
button { cursor: pointer; }
button:disabled { opacity: 0.5; cursor: not-allowed; }
.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.message { margin-bottom: 12px; font-size: 14px; }
.message.error { color: #b91c1c; }
.db-section { margin-bottom: 18px; padding: 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.section-header h2 { font-size: 18px; }
.db-section h3 { margin: 18px 0 8px; font-size: 15px; }
.status-badge { padding: 3px 9px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.status-badge.ok { background: #dcfce7; color: #15803d; }
.status-badge.bad { background: #fee2e2; color: #b91c1c; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.metrics-grid.compact { grid-template-columns: repeat(6, 1fr); }
.metric { padding: 10px; border: 1px solid #e5e7eb; border-radius: 6px; background: #f8fafc; }
.metric span { display: block; color: #6b7280; font-size: 12px; margin-bottom: 5px; }
.metric strong { font-size: 15px; }
.warning { color: #b45309; }
.severe { color: #b91c1c; }
.db-error { margin-top: 12px; color: #b91c1c; font-size: 13px; }
.table-shell { overflow-x: auto; border: 1px solid #e5e7eb; border-radius: 8px; }
table { width: 100%; min-width: 760px; border-collapse: collapse; }
th, td { padding: 9px 10px; border-bottom: 1px solid #e5e7eb; text-align: left; }
th { background: #f8fafc; color: #4b5563; font-size: 13px; }
.sql-cell { max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { text-align: center; color: #6b7280; padding: 24px; }
.history-filters { flex-wrap: wrap; margin-bottom: 12px; }
.history-filters label { display: flex; flex-direction: column; gap: 4px; color: #4b5563; font-size: 12px; }
.pagination { margin-top: 12px; color: #4b5563; }
@media (max-width: 900px) { .metrics-grid { grid-template-columns: repeat(2, 1fr); } .metrics-grid.compact { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 600px) { .page-header, .header-actions, .history-filters { flex-direction: column; align-items: stretch; } .metrics-grid, .metrics-grid.compact { grid-template-columns: 1fr; } }
</style>
