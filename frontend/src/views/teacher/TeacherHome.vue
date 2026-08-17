<!-- 教师端工作台：题库/考试/学生/提交等总览 -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>工作台</h1>
        <p>题库 · 考试 · 学情总览</p>
      </div>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <!-- 统计卡片 -->
    <section class="cards">
      <div v-for="card in cards" :key="card.label" class="stat-card">
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </section>

    <div class="two-col">
      <!-- 分类题目分布 -->
      <section class="panel">
        <h2>分类题目分布</h2>
        <div v-if="categories.length === 0" class="empty">暂无分类</div>
        <div v-else class="category-list">
          <div v-for="item in categories" :key="item.name" class="category-row">
            <span class="category-name">{{ item.name }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(item.count) }"></div>
            </div>
            <span class="category-count">{{ item.count }}</span>
          </div>
        </div>
      </section>

      <!-- 最近考试 -->
      <section class="panel">
        <h2>最近考试</h2>
        <div v-if="recentExams.length === 0" class="empty">暂无考试</div>
        <table v-else class="mini-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>状态</th>
              <th>题目数</th>
              <th>总分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="exam in recentExams" :key="exam.id">
              <td>{{ exam.title }}</td>
              <td>
                <span class="status-badge" :class="statusClass(exam.status)">
                  {{ statusText[exam.status] || exam.status }}
                </span>
              </td>
              <td>{{ exam.questionCount }}</td>
              <td>{{ exam.totalScore }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboardStats } from '../../api/teacher'

const stats = ref(null)
const error = ref('')

const statusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束'
}

// 统计卡片数据（从后端聚合结果映射而来）
const cards = computed(() => {
  if (!stats.value) return []
  const s = stats.value
  return [
    { label: '题目总数', value: s.questionCount },
    { label: '已发布题目', value: s.publishedQuestionCount },
    { label: '考试总数', value: s.examCount },
    { label: '已发布考试', value: s.publishedExamCount },
    { label: '学生总数', value: s.studentCount },
    { label: '提交总数', value: s.submissionCount }
  ]
})

// 分类分布：把 { 分类名: 数量 } 映射成 [{ name, count }]
const categories = computed(() => {
  if (!stats.value) return []
  return Object.entries(stats.value.categoryDistribution || {}).map(([name, count]) => ({
    name,
    count
  }))
})

const recentExams = computed(() => stats.value?.recentExams || [])

function statusClass(status) {
  return {
    'status-draft': status === 'DRAFT',
    'status-published': status === 'PUBLISHED',
    'status-closed': status === 'CLOSED'
  }
}

// 按最大值归一化，算出条形宽度百分比
function barWidth(count) {
  const max = Math.max(1, ...categories.value.map((c) => c.count))
  return `${(count / max) * 100}%`
}

async function load() {
  error.value = ''
  try {
    const res = await getDashboardStats()
    stats.value = res.data
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header h1 {
  font-size: 24px;
  margin-bottom: 6px;
}

.page-header p {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 20px;
}

.error-banner {
  color: #b91c1c;
  font-size: 14px;
  margin-bottom: 12px;
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
  font-size: 30px;
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

.empty {
  padding: 24px 0;
  text-align: center;
  color: #9ca3af;
}

.category-list {
  display: grid;
  gap: 12px;
}

.category-row {
  display: grid;
  grid-template-columns: 90px 1fr 40px;
  align-items: center;
  gap: 10px;
}

.category-name {
  font-size: 13px;
  color: #4b5563;
}

.category-count {
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

.mini-table {
  width: 100%;
  border-collapse: collapse;
}

.mini-table th,
.mini-table td {
  padding: 9px 8px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  font-size: 13px;
}

.mini-table th {
  color: #6b7280;
  font-weight: 600;
  background: #f8fafc;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-draft {
  background: #f3f4f6;
  color: #4b5563;
}

.status-published {
  background: #d1fae5;
  color: #047857;
}

.status-closed {
  background: #fee2e2;
  color: #b91c1c;
}
</style>
