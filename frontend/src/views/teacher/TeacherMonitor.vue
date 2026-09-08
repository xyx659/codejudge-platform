<!-- 教师端：考试监考（实时轮询学生作答状态与预警） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>考试监考</h1>
        <p>实时跟踪学生作答进度与异常预警</p>
      </div>
      <label class="exam-select">
        <span>选择考试</span>
        <select v-model="examId" @change="onExamChange">
          <option value="" disabled>请选择考试</option>
          <option v-for="e in exams" :key="e.id" :value="e.id">
            {{ e.title }}（{{ statusText[e.status] || e.status }}）
          </option>
        </select>
      </label>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <template v-if="summary">
      <!-- 进度总览卡片 -->
      <section class="cards">
        <div class="stat-card">
          <div class="stat-value">{{ summary.totalStudents }}</div>
          <div class="stat-label">应考人数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ summary.submittedCount }}</div>
          <div class="stat-label">已提交</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ formatPercent(summary.totalStudents, summary.submittedCount) }}</div>
          <div class="stat-label">提交率</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ summary.avgScore.toFixed(1) }}</div>
          <div class="stat-label">平均分</div>
        </div>
      </section>

      <!-- 预警（分页 + 时间） -->
      <section v-if="alerts.length > 0" class="panel alerts-panel">
        <h2>预警</h2>
        <ul class="alert-list">
          <li v-for="(a, i) in alerts" :key="i" class="alert-item">
            <span v-if="a.time" class="alert-time">{{ formatAlertTime(a.time) }}</span>
            <strong>{{ a.name }}</strong> · {{ a.type }}：{{ a.message }}
          </li>
        </ul>
        <div v-if="alertTotalPages > 1" class="alert-pager">
          <button class="pager-btn" :disabled="alertPage === 0" @click="prevAlertPage">上一页</button>
          <span class="pager-info">第 {{ alertPage + 1 }} / {{ alertTotalPages }} 页 · 共 {{ alertTotal }} 条</span>
          <button class="pager-btn" :disabled="alertPage >= alertTotalPages - 1" @click="nextAlertPage">下一页</button>
        </div>
      </section>

      <!-- 学生作答状态表 -->
      <section class="panel">
        <h2>学生作答状态</h2>
        <div class="table-shell">
          <table>
            <thead>
              <tr>
                <th>学号</th>
                <th>姓名</th>
                <th>作答进度</th>
                <th>当前得分</th>
                <th>状态</th>
                <th>切屏</th>
                <th>切页面</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in summary.students" :key="s.studentId">
                <td>{{ s.studentNo || '-' }}</td>
                <td>{{ s.name || '-' }}</td>
                <td>
                  <div class="progress-cell">
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: progressWidth(s) }"></div>
                    </div>
                    <span class="progress-text">{{ s.submittedCount }} / {{ s.totalQuestions }}</span>
                  </div>
                </td>
                <td>{{ s.score }}</td>
                <td>
                  <span class="status-badge" :class="studentStatusClass(s.status)">{{ s.status }}</span>
                </td>
                <td>
                  <span class="cheat-count" :class="{ warn: s.switchTabCount > 0 }">{{ s.switchTabCount }}</span>
                </td>
                <td>
                  <span class="cheat-count" :class="{ warn: s.leavePageCount > 0 }">{{ s.leavePageCount }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <p class="polling-note">每 5 秒自动刷新 · 上次更新：{{ lastUpdate }}</p>
    </template>

    <div v-else-if="!error && examId" class="empty">加载中...</div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getMonitor, getMonitorAlerts, listExams } from '../../api/teacher'

const statusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束'
}

const exams = ref([])
const examId = ref('')
const summary = ref(null)
const error = ref('')
const lastUpdate = ref('')
let timer = null

// 预警分页
const alerts = ref([])
const alertPage = ref(0)
const alertSize = ref(10)
const alertTotal = ref(0)
const alertTotalPages = computed(() => Math.max(1, Math.ceil(alertTotal.value / alertSize.value)))

// 学生状态配色
function studentStatusClass(status) {
  if (status === '已交卷') return 'status-done'
  if (status === '答题中') return 'status-doing'
  return 'status-pending'
}

function progressWidth(s) {
  if (!s.totalQuestions) return '0%'
  const pct = Math.min(100, (s.submittedCount / s.totalQuestions) * 100)
  return `${pct}%`
}

function formatPercent(total, part) {
  if (!total) return '0%'
  return `${((part / total) * 100).toFixed(0)}%`
}

async function loadExams() {
  try {
    const res = await listExams({ size: 1000 })
    exams.value = res.data.list || []
    // 默认选第一场「已发布」的考试，否则选第一场
    const published = exams.value.find((e) => e.status === 'PUBLISHED')
    if (published) {
      examId.value = published.id
    } else if (exams.value.length > 0) {
      examId.value = exams.value[0].id
    }
    if (examId.value) {
      await loadMonitor()
      startPolling()
    }
  } catch (e) {
    error.value = e.message || '考试列表加载失败'
  }
}

async function loadMonitor() {
  if (!examId.value) return
  try {
    const res = await getMonitor(examId.value)
    summary.value = res.data
    error.value = ''
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    await loadAlerts()
  } catch (e) {
    error.value = e.message || '监考数据加载失败'
  }
}

async function loadAlerts() {
  if (!examId.value) return
  try {
    const res = await getMonitorAlerts(examId.value, { page: alertPage.value, size: alertSize.value })
    alerts.value = res.data.list || []
    alertTotal.value = res.data.total || 0
  } catch (e) {
    // 预警加载失败不阻断整页，静默保留上一份数据
  }
}

function prevAlertPage() {
  if (alertPage.value > 0) {
    alertPage.value -= 1
    loadAlerts()
  }
}

function nextAlertPage() {
  if (alertPage.value < alertTotalPages.value - 1) {
    alertPage.value += 1
    loadAlerts()
  }
}

function formatAlertTime(s) {
  return s ? s.replace('T', ' ').slice(0, 19) : ''
}

function startPolling() {
  stopPolling()
  timer = setInterval(loadMonitor, 5000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function onExamChange() {
  stopPolling()
  alertPage.value = 0
  alerts.value = []
  if (examId.value) {
    loadMonitor()
    startPolling()
  }
}

onMounted(loadExams)
onUnmounted(stopPolling)
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
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

.exam-select {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 14px;
}

.exam-select select {
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  font: inherit;
}

.error-banner {
  color: #b91c1c;
  font-size: 14px;
  margin-bottom: 12px;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 18px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #059669;
}

.stat-label {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}

.panel {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  margin-bottom: 18px;
}

.panel h2 {
  font-size: 16px;
  margin-bottom: 14px;
}

.alerts-panel {
  border-color: #fecaca;
  background: #fff7f7;
}

.alert-list {
  list-style: none;
  display: grid;
  gap: 8px;
}

.alert-item {
  padding: 10px 12px;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 14px;
}

.alert-time {
  margin-right: 8px;
  color: #9ca3af;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.alert-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.pager-btn {
  padding: 4px 12px;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: #fff;
  color: #b91c1c;
  cursor: pointer;
  font-size: 13px;
}

.pager-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pager-info {
  color: #9ca3af;
  font-size: 13px;
}

.table-shell {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}

th {
  background: #f8fafc;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: #f0fdf4;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: #059669;
}

.progress-text {
  font-size: 13px;
  color: #4b5563;
  white-space: nowrap;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-done {
  background: #d1fae5;
  color: #047857;
}

.status-doing {
  background: #dbeafe;
  color: #1d4ed8;
}

.status-pending {
  background: #f3f4f6;
  color: #6b7280;
}

.cheat-count {
  display: inline-block;
  min-width: 24px;
  text-align: center;
  font-weight: 600;
  color: #9ca3af;
}

.cheat-count.warn {
  color: #dc2626;
}

.polling-note {
  color: #9ca3af;
  font-size: 12px;
  text-align: right;
}
</style>
