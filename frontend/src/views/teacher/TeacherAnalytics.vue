<!-- 教师端：学情分析（成绩统计、分数段分布、能力画像、逐题掌握度、教学诊断报告） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>学情分析</h1>
        <p>成绩统计 · 能力画像 · 教学诊断</p>
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
          <div v-else ref="distRef" class="chart"></div>
        </section>

        <!-- 能力画像 · 知识点（雷达图） -->
        <section class="panel">
          <h2>能力画像 · 知识点</h2>
          <div v-if="knowledgeAbilities.length < 3" class="empty">
            知识点少于 3 个，暂不展示雷达图
          </div>
          <div v-else ref="radarRef" class="chart"></div>
        </section>
      </div>

      <div class="two-col">
        <!-- 能力画像 · 难度（横向条形图） -->
        <section class="panel">
          <h2>能力画像 · 难度</h2>
          <div v-if="difficultyAbilities.length === 0" class="empty">暂无数据</div>
          <div v-else ref="diffRef" class="chart"></div>
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

      <!-- 教学诊断报告 -->
      <section class="panel report-panel">
        <div class="report-header">
          <h2>教学诊断报告</h2>
          <button class="report-btn" @click="generateReport" :disabled="reportLoading">
            {{ reportLoading ? '生成中…' : report ? '重新生成' : '生成报告' }}
          </button>
        </div>
        <p v-if="reportError" class="error-banner">{{ reportError }}</p>
        <template v-if="report">
          <p class="report-conclusion">{{ report.conclusion }}</p>
          <div class="report-grid">
            <div v-if="report.weakDimensions.length" class="report-block">
              <h3>薄弱知识点</h3>
              <ul><li v-for="(w, i) in report.weakDimensions" :key="i">{{ w }}</li></ul>
            </div>
            <div v-if="report.weakQuestions.length" class="report-block">
              <h3>薄弱题目</h3>
              <ul><li v-for="(w, i) in report.weakQuestions" :key="i">{{ w }}</li></ul>
            </div>
            <div v-if="report.lowScoreStudents.length" class="report-block">
              <h3>重点关注学生</h3>
              <ul><li v-for="(w, i) in report.lowScoreStudents" :key="i">{{ w }}</li></ul>
            </div>
            <div v-if="report.suggestions.length" class="report-block">
              <h3>教学建议</h3>
              <ul><li v-for="(w, i) in report.suggestions" :key="i">{{ w }}</li></ul>
            </div>
          </div>
        </template>
      </section>
    </template>

    <div v-else-if="!error && examId" class="empty">加载中...</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getAnalytics, getDiagnosisReport, listExams } from '../../api/teacher'

const statusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束'
}

const exams = ref([])
const examId = ref('')
const analytics = ref(null)
const error = ref('')
const report = ref(null)
const reportError = ref('')
const reportLoading = ref(false)

const stats = computed(() => analytics.value?.stats || {})
const distribution = computed(() => analytics.value?.distribution || [])
const abilities = computed(() => analytics.value?.abilities || [])
const knowledgeAbilities = computed(() => analytics.value?.knowledgeAbilities || [])
const difficultyAbilities = computed(() => analytics.value?.difficultyAbilities || [])

// 图表容器与实例（统一用绿色主色，与全站视觉一致）
const distRef = ref(null)
const radarRef = ref(null)
const diffRef = ref(null)
const charts = []

function disposeCharts() {
  charts.forEach((c) => c && c.dispose())
  charts.length = 0
}

function initChart(el) {
  if (!el) return null
  const chart = echarts.init(el)
  charts.push(chart)
  return chart
}

function renderCharts() {
  disposeCharts()
  renderDistribution()
  renderRadar()
  renderDifficulty()
}

function renderDistribution() {
  if (!distRef.value || distribution.value.length === 0) return
  const chart = initChart(distRef.value)
  chart.setOption({
    grid: { left: 40, right: 16, top: 28, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: distribution.value.map((b) => b.label) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: distribution.value.map((b) => b.count),
        barWidth: '50%',
        itemStyle: { color: '#059669', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top' }
      }
    ]
  })
}

function renderRadar() {
  if (!radarRef.value || knowledgeAbilities.value.length < 3) return
  const chart = initChart(radarRef.value)
  chart.setOption({
    tooltip: {},
    radar: {
      indicator: knowledgeAbilities.value.map((d) => ({ name: d.name, max: 100 })),
      radius: '65%',
      axisName: { color: '#4b5563' },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
      splitArea: { areaStyle: { color: ['#fff', '#f8fafc'] } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: knowledgeAbilities.value.map((d) => d.avgScoreRate),
            name: '得分率',
            areaStyle: { color: 'rgba(5, 150, 105, 0.2)' },
            lineStyle: { color: '#059669' },
            itemStyle: { color: '#059669' }
          }
        ]
      }
    ]
  })
}

function renderDifficulty() {
  if (!diffRef.value || difficultyAbilities.value.length === 0) return
  const chart = initChart(diffRef.value)
  const dims = difficultyAbilities.value
  chart.setOption({
    grid: { left: 56, right: 48, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', max: 100 },
    yAxis: { type: 'category', data: dims.map((d) => d.name) },
    series: [
      {
        type: 'bar',
        data: dims.map((d) => d.avgScoreRate),
        barWidth: '55%',
        itemStyle: { color: '#059669', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', formatter: '{c}%' }
      }
    ]
  })
}

function handleResize() {
  charts.forEach((c) => c && c.resize())
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
  report.value = null
  try {
    const res = await getAnalytics(examId.value)
    analytics.value = res.data
    await nextTick()
    renderCharts()
  } catch (e) {
    error.value = e.message || '学情分析加载失败'
  }
}

async function generateReport() {
  if (!examId.value) return
  reportLoading.value = true
  reportError.value = ''
  report.value = null
  try {
    const res = await getDiagnosisReport(examId.value)
    report.value = res.data
  } catch (e) {
    reportError.value = e.message || '报告生成失败'
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadExams()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
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
  margin-bottom: 16px;
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

.chart {
  width: 100%;
  height: 300px;
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

.report-panel {
  margin-top: 0;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.report-btn {
  padding: 7px 16px;
  border: 1px solid #059669;
  border-radius: 6px;
  background: #059669;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.report-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.report-conclusion {
  padding: 12px 14px;
  margin: 12px 0;
  border-left: 3px solid #059669;
  background: #f0fdf4;
  color: #374151;
  font-size: 14px;
  line-height: 1.6;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.report-block {
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.report-block h3 {
  font-size: 14px;
  margin-bottom: 8px;
  color: #374151;
}

.report-block ul {
  margin: 0;
  padding-left: 18px;
}

.report-block li {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.7;
}
</style>
