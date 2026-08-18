<!-- 学生端：考试首页，展示已发布题目列表，支持难度/标签筛选与分页 -->
<template>
  <div class="page">
    <h1>考试首页</h1>
    <p class="desc">选择一道题目开始答题，提交后自动判卷</p>

    <!-- 筛选栏 -->
    <div class="filters">
      <select v-model="difficulty" class="select">
        <option value="">全部难度</option>
        <option value="简单">简单</option>
        <option value="中等">中等</option>
        <option value="困难">困难</option>
      </select>
      <input
        v-model="tag"
        class="input"
        type="text"
        placeholder="按标签筛选，如：数学"
        @keyup.enter="search"
      />
      <button class="btn" @click="search">查询</button>
    </div>

    <!-- 加载 / 空 / 错误状态 -->
    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>
    <p v-else-if="questions.length === 0" class="hint">暂无已发布的题目</p>

    <!-- 题目列表 -->
    <div v-else class="list">
      <div v-for="q in questions" :key="q.id" class="card question">
        <div class="question-head">
          <h3 class="title">{{ q.title }}</h3>
          <span class="badge" :class="difficultyClass(q.difficulty)">
            {{ q.difficulty }}
          </span>
        </div>
        <div class="meta">
          <span>语言：{{ q.language }}</span>
          <span>方法：{{ q.methodName }}</span>
          <span v-if="q.tags && q.tags.length">
            标签：{{ q.tags.join('、') }}
          </span>
        </div>
        <button class="btn primary" @click="goSolve(q.id)">开始答题</button>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="totalPages > 1">
      <button class="btn" :disabled="page <= 0" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page + 1 }} / {{ totalPages }} 页（共 {{ total }} 题）</span>
      <button class="btn" :disabled="page >= totalPages - 1" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listQuestions } from '../../api/student'
import { difficultyClass } from '../../utils/format'

const router = useRouter()

const questions = ref([])
const difficulty = ref('')
const tag = ref('')
const page = ref(0)
const size = ref(10)
const total = ref(0)
const totalPages = ref(1)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listQuestions({
      page: page.value,
      size: size.value,
      difficulty: difficulty.value,
      tag: tag.value
    })
    questions.value = res.data.list || []
    total.value = res.data.total || 0
    totalPages.value = res.data.size ? Math.ceil(total.value / res.data.size) : 1
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 0
  load()
}

function changePage(p) {
  page.value = p
  load()
}

function goSolve(id) {
  router.push(`/student/questions/${id}`)
}

onMounted(load)
</script>

<style scoped>
.page h1 {
  font-size: 24px;
  margin-bottom: 8px;
}

.page .desc {
  color: #6b7280;
  margin-bottom: 20px;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.select,
.input {
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  background: #fff;
}

.input {
  flex: 1;
  max-width: 260px;
}

.btn {
  padding: 8px 16px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  font-size: 14px;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.hint {
  color: #6b7280;
  padding: 20px 0;
}

.hint.error {
  color: #dc2626;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.question {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 17px;
}

.badge {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}

.badge.easy {
  background: #16a34a;
}

.badge.medium {
  background: #d97706;
}

.badge.hard {
  background: #dc2626;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 14px;
}

.question .btn {
  align-self: flex-start;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  color: #6b7280;
  font-size: 14px;
}
</style>
