<!-- 教师端：学情分析（成绩统计、分数段分布、逐题掌握度） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>学情分析</h1>
        <p>成绩统计 · 分数段分布 · 逐题掌握度</p>
      </div>
      <label class="exam-select">
        <span>选择考试</span>
        <select v-model="examId" @change="load">
          <option value="" disabled>请选择考试</option>
          <option v-for="e in exams" :key="e.id" :value="e.id">
            {{ e.title }}（{{ statusText[e.status] || e.status }}）
          </option>
        </select>
      </label>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <template v-if="analytics">
      <!-- 成绩统计 -->
      <section class="cards">
        <div class="stat-card">
          <div class="stat-value">{{ stats.submittedCount }}/{{ stats.totalStudents }}</div>
          <div class="stat-label">已提交 / 应考</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.avgScore.toFixed(1) }}</div>
          <div class="stat-label">平均分</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.maxScore.toFixed(1) }}</div>
          <div class="stat-label">最高分</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.minScore.toFixed(1) }}</div>
          <div class="stat-label">最低分</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.passRate.toFixed(1) }}%</div>
          <div class="stat-label">及格率（≥{{ stats.passScore }} 分）</div>
        </div>
      </section>

      <div class="two-col">
        <!-- 分数段分布 -->
        <section class="panel">
          <h2>分数段分布</h2>
          <div v-if="distribution.length === 0" class="empty">暂无数据</div>
          <div v-else class="dist-list">
            <div v-for="b in distribution" :key="b.label" class="dist-row">
              <span class="dist-label">{{ b.label }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: bucketWidth(b.count) }"></div>
              </div>
              <span class="dist-count">{{ b.count }}</span>
            </div>
          </div>
        </section>

        <!-- 逐题掌握度 -->
        <section class="panel">
          <h2>逐题掌握度</h2>
          <div v-if="abilities.length === 0" class="empty">暂无数据</div>
          <table v-else class="ability-table">
            <thead>
              <tr>
                <th>题目</th>
                <th>平均分 / 满分</th>
                <th>完成率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(a, i) in abilities" :key="i">
                <td class="ability-title">{{ a.questionTitle }}</td>
                <td>{{ a.avgScore.toFixed(1) }} / {{ a.fullScore }}</td>
                <td>
                  <div class="progress-cell">
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: `${a.completionRate}%` }"></div>
                    </div>
                    <span class="progress-text">{{ a.completionRate.toFixed(0) }}%</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </template>

    <div v-else-if="!error && examId" class="empty">加载中...</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAnalytics, listExams } from '../../api/teacher'

const statusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束'
}

const exams = ref([])
const examId = ref('')
const analytics = ref(null)
const error = ref('')

const stats = computed(() => analytics.value?.stats || {})
const distribution = computed(() => analytics.value?.distribution || [])
const abilities = computed(() => analytics.value?.abilities || [])

function bucketWidth(count) {
  const max = Math.max(1, ...distribution.value.map((b) => b.count))
  return `${(count / max) * 100}%`
}

async function loadExams() {
  try {
    const res = await listExams({ size: 1000 })
    exams.value = res.data.list || []
    if (exams.value.length > 0) {
      examId.value = exams.value[0].id
      await load()
    }
  } catch (e) {
    error.value = e.message || '考试列表加载失败'
  }
}

async function load() {
  if (!examId.value) return
  error.value = ''
  try {
    const res = await getAnalytics(examId.value)
    analytics.value = res.data
  } catch (e) {
    error.value = e.message || '学情分析加载失败'
  }
}

onMounted(loadExams)
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
  font-size: 26px;
  font-weight: 700;
  color: #059669;
}

.stat-label {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 820px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

.panel {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.panel h2 {
  font-size: 16px;
  margin-bottom: 14px;
}

.dist-list {
  display: grid;
  gap: 12px;
}

.dist-row {
  display: grid;
  grid-template-columns: 70px 1fr 40px;
  align-items: center;
  gap: 10px;
}

.dist-label {
  font-size: 13px;
  color: #4b5563;
}

.dist-count {
  font-size: 13px;
  text-align: right;
  color: #059669;
  font-weight: 600;
}

.bar-track {
  height: 10px;
  border-radius: 999px;
  background: #f0fdf4;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: #059669;
}

.ability-table {
  width: 100%;
  border-collapse: collapse;
}

.ability-table th,
.ability-table td {
  padding: 10px 8px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  font-size: 13px;
}

.ability-table th {
  background: #f8fafc;
  color: #6b7280;
  font-weight: 600;
}

.ability-title {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}

.progress-cell .bar-track {
  flex: 1;
  height: 8px;
}

.progress-text {
  font-size: 12px;
  color: #4b5563;
  white-space: nowrap;
}
</style>
