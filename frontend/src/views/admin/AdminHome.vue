<!-- 管理端：工作台，展示系统真实统计概览 -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>工作台</h1>
        <p>用户、题库、提交、配置与数据库概览</p>
      </div>
      <button type="button" class="secondary" :disabled="loading" @click="loadDashboard">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section v-if="dashboard" class="dashboard">
      <div class="metrics-grid">
        <div class="metric">
          <span>学生</span>
          <strong>{{ dashboard.studentCount }}</strong>
        </div>
        <div class="metric">
          <span>教师</span>
          <strong>{{ dashboard.teacherCount }}</strong>
        </div>
        <div class="metric">
          <span>管理员</span>
          <strong>{{ dashboard.adminCount }}</strong>
        </div>
        <div class="metric">
          <span>题目总数</span>
          <strong>{{ dashboard.questionCount }}</strong>
        </div>
        <div class="metric">
          <span>已发布题目</span>
          <strong>{{ dashboard.publishedQuestionCount }}</strong>
        </div>
        <div class="metric">
          <span>提交记录</span>
          <strong>{{ dashboard.submissionCount }}</strong>
        </div>
        <div class="metric">
          <span>系统配置</span>
          <strong>{{ dashboard.systemConfigCount }}</strong>
        </div>
        <div class="metric">
          <span>审计日志</span>
          <strong>{{ dashboard.auditLogCount }}</strong>
        </div>
      </div>

      <div class="status-row">
        <div class="status-card" :class="dashboard.mysqlOk ? 'ok' : 'bad'">
          <span>MySQL</span>
          <strong>{{ dashboard.mysqlOk ? '正常' : '异常' }}</strong>
        </div>
        <div class="status-card" :class="dashboard.mongoOk ? 'ok' : 'bad'">
          <span>MongoDB</span>
          <strong>{{ dashboard.mongoOk ? '正常' : '异常' }}</strong>
        </div>
      </div>

      <section class="recent-logs">
        <h2>最近操作</h2>
        <div class="table-shell">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>用户</th>
                <th>模块</th>
                <th>操作</th>
                <th>结果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="dashboard.recentAuditLogs.length === 0">
                <td colspan="5" class="empty-cell">暂无操作日志</td>
              </tr>
              <tr v-for="log in dashboard.recentAuditLogs" :key="log.id" v-else>
                <td>{{ formatDate(log.createdAt) }}</td>
                <td>{{ log.username || '-' }}</td>
                <td>{{ log.module || '-' }}</td>
                <td>{{ log.operation || '-' }}</td>
                <td>
                  <span class="badge" :class="log.success ? 'success' : 'failed'">
                    {{ log.success ? '成功' : '失败' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>

    <div v-else-if="loading" class="empty">加载中...</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAdminDashboard } from '../../api/admin'

const dashboard = ref(null)
const loading = ref(false)
const error = ref('')

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const response = await getAdminDashboard()
    dashboard.value = response.data
  } catch (e) {
    error.value = e.message || '工作台数据加载失败'
  } finally {
    loading.value = false
  }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-header h1 { font-size: 24px; margin-bottom: 6px; }
.page-header p { color: #6b7280; font-size: 14px; }
.error-banner { color: #b91c1c; margin-bottom: 12px; }
.dashboard { display: flex; flex-direction: column; gap: 18px; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.metric, .status-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; }
.metric span, .status-card span { display: block; color: #6b7280; font-size: 13px; margin-bottom: 8px; }
.metric strong, .status-card strong { display: block; font-size: 26px; color: #1f2937; }
.status-row { display: flex; gap: 12px; }
.status-card { min-width: 180px; }
.status-card.ok strong { color: #15803d; }
.status-card.bad strong { color: #b91c1c; }
.recent-logs { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; }
.recent-logs h2 { font-size: 18px; margin-bottom: 12px; }
.table-shell { overflow-x: auto; }
table { width: 100%; min-width: 680px; border-collapse: collapse; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: left; }
th { background: #f9fafb; color: #374151; }
.empty-cell { text-align: center; color: #6b7280; padding: 26px 0; }
.badge { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 12px; }
.badge.success { background: #dcfce7; color: #15803d; }
.badge.failed { background: #fee2e2; color: #b91c1c; }
button.secondary { min-height: 36px; padding: 0 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #374151; cursor: pointer; }
.empty { padding: 40px; text-align: center; color: #6b7280; }
@media (max-width: 900px) {
  .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .metrics-grid { grid-template-columns: 1fr; }
  .status-row { flex-direction: column; }
}
</style>
