<!-- 学生端：我的考试（试卷列表），点进试卷后按时间窗答题 -->
<template>
  <div class="page">
    <h1>我的考试</h1>
    <p class="desc">选择一场考试进入，考试开始后方可作答，结束后自动交卷</p>

    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>
    <p v-else-if="exams.length === 0" class="hint">暂无考试</p>

    <div v-else class="list">
      <div v-for="e in exams" :key="e.id" class="card exam" @click="goExam(e.id)">
        <div class="exam-head">
          <h3 class="title">{{ e.title }}</h3>
          <span class="badge" :class="statusClass(e.status)">{{ statusText(e.status) }}</span>
          <span v-if="e.submitted" class="badge done">已交卷</span>
        </div>
        <div class="meta">
          <span v-if="e.targetClass">班级：{{ e.targetClass }}</span>
          <span>题目：{{ e.questionCount }} 题</span>
          <span>总分：{{ e.totalScore }}</span>
        </div>
        <div class="time">
          时间：{{ formatTime(e.startTime) }} ~ {{ formatTime(e.endTime) }}
          <span v-if="e.durationMinutes != null">（{{ e.durationMinutes }} 分钟）</span>
        </div>
        <button class="btn primary">{{ enterText(e) }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listExams } from '../../api/student'

const router = useRouter()

const exams = ref([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listExams()
    exams.value = res.data || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function goExam(id) {
  router.push(`/student/exams/${id}`)
}

function statusText(status) {
  if (status === 'NOT_STARTED') return '未开始'
  if (status === 'ONGOING') return '进行中'
  if (status === 'ENDED') return '已结束'
  return status || '未知'
}

function statusClass(status) {
  if (status === 'ONGOING') return 'ongoing'
  if (status === 'NOT_STARTED') return 'notstarted'
  if (status === 'ENDED') return 'ended'
  return ''
}

function enterText(e) {
  if (e.submitted) return '查看答卷'
  if (e.status === 'NOT_STARTED') return '查看题目'
  if (e.status === 'ONGOING') return '进入考试'
  return '查看结果'
}

function formatTime(s) {
  return s ? s.replace('T', ' ') : '—'
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

.exam {
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
  transition: box-shadow 0.15s;
}

.exam:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.exam-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 17px;
}

.badge {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.badge.ongoing {
  background: #16a34a;
}

.badge.notstarted {
  background: #2563eb;
}

.badge.ended {
  background: #9ca3af;
}

.badge.done {
  background: #16a34a;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 14px;
}

.time {
  color: #6b7280;
  font-size: 14px;
}

.btn {
  padding: 8px 16px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  font-size: 14px;
  align-self: flex-start;
}

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
</style>
