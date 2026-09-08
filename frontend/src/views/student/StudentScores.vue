<!-- 学生端：我的成绩，按考试分组汇总，点开考试看每题得分，再点某题查看 AI 评审 -->
<template>
  <div class="page">
    <h1>我的成绩</h1>
    <p class="desc">按考试分组查看得分，点开考试看每题得分，点某题查看 AI 评审</p>

    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>
    <p v-else-if="exams.length === 0" class="hint">暂无成绩，去考试首页参加一场考试吧</p>

    <template v-else>
      <div v-for="g in exams" :key="g.examId || 'legacy'" class="card exam-group">
        <div class="exam-head" @click="toggle(g)">
          <div class="head-left">
            <h3 class="title">{{ g.examTitle }}</h3>
            <span class="meta">{{ g.questions.length }} 题</span>
          </div>
          <div class="head-right">
            <span class="score">{{ scoreText(g) }}</span>
            <span v-if="g.passScore != null" class="pass" :class="passClass(g)">{{ passText(g) }}</span>
            <span class="arrow">{{ expanded === g.examId ? '▲' : '▼' }}</span>
          </div>
        </div>

        <div v-if="expanded === g.examId" class="questions">
          <div
            v-for="q in g.questions"
            :key="q.submissionId"
            class="q-row"
            :class="{ active: selectedId === q.submissionId }"
            @click="openDetail(q.submissionId)"
          >
            <span class="q-title">{{ q.questionTitle || '（题目已删除）' }}</span>
            <span class="status" :class="statusClass(q.judgeStatus)">
              {{ judgeStatusText(q.judgeStatus) }}
            </span>
            <span class="q-score">{{ q.score == null ? '—' : q.score }} 分</span>
            <span class="link">AI 评审 →</span>
          </div>
        </div>
      </div>

      <!-- 成绩详情（用例结果 + AI 评审） -->
      <div v-if="detailLoading" class="card detail">详情加载中...</div>
      <div v-else-if="detailError" class="card detail error">{{ detailError }}</div>
      <div v-else-if="detail" class="card detail">
        <div class="detail-head">
          <h3>{{ detail.questionTitle || '题目' }}</h3>
          <span class="score">{{ detail.score == null ? '—' : detail.score }} 分</span>
          <button class="close" @click="closeDetail">×</button>
        </div>

        <!-- 用例结果 -->
        <div class="label">测试用例</div>
        <table v-if="detail.testResults && detail.testResults.length" class="sub">
          <thead>
            <tr><th>用例</th><th>结果</th><th>实际输出</th><th>说明</th><th>耗时</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in detail.testResults" :key="i">
              <td>{{ r.testCaseName }}</td>
              <td><span :class="r.passed ? 'ok' : 'fail'">{{ r.passed ? '通过' : '失败' }}</span></td>
              <td><code>{{ r.actual }}</code></td>
              <td>{{ r.message }}</td>
              <td>{{ r.durationMs }}ms</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="hint">暂无用例结果</p>

        <!-- AI 评审 -->
        <div class="label">AI 评审</div>
        <div v-if="detail.aiReview" class="ai">
          <div class="ai-stats">
            <div class="stat"><span>综合分</span><b>{{ detail.aiReview.score ?? '—' }}</b></div>
            <div class="stat"><span>通过率</span><b>{{ detail.aiReview.passRate ?? '—' }}%</b></div>
            <div class="stat"><span>代码质量</span><b>{{ detail.aiReview.qualityScore ?? '—' }}</b></div>
          </div>
          <ul v-if="detail.aiReview.feedback && detail.aiReview.feedback.length" class="feedback">
            <li v-for="(f, i) in detail.aiReview.feedback" :key="i">{{ f }}</li>
          </ul>
        </div>
        <p v-else class="hint">暂无 AI 反馈</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getSubmissionResult, listExamScores } from '../../api/student'
import { judgeStatusText } from '../../utils/format'

const exams = ref([])
const expanded = ref(null)
const loading = ref(false)
const error = ref('')

const selectedId = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listExamScores()
    exams.value = res.data || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function toggle(g) {
  expanded.value = expanded.value === g.examId ? '' : g.examId
}

function scoreText(g) {
  return g.fullScore > 0 ? `${g.achievedScore} / ${g.fullScore} 分` : `${g.achievedScore} 分`
}

function passText(g) {
  return g.achievedScore >= g.passScore ? '已及格' : '未及格'
}

function passClass(g) {
  return g.achievedScore >= g.passScore ? 'passed' : 'failed'
}

async function openDetail(id) {
  selectedId.value = id
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const res = await getSubmissionResult(id)
    detail.value = res.data
  } catch (e) {
    detailError.value = e.message || '加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  selectedId.value = null
  detail.value = null
}

function statusClass(status) {
  if (status === 'RUN_COMPLETED') return 'done'
  if (status === 'COMPILE_ERROR') return 'err'
  if (status === 'TIMEOUT') return 'err'
  return 'pending'
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

.hint.error,
.error {
  color: #dc2626;
}

.exam-group {
  margin-bottom: 12px;
  padding: 0;
  overflow: hidden;
}

.exam-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  cursor: pointer;
}

.exam-head:hover {
  background: #f9fafb;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  font-size: 17px;
}

.meta {
  color: #6b7280;
  font-size: 13px;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.score {
  font-size: 18px;
  font-weight: 700;
  color: #2563eb;
}

.pass {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.pass.passed {
  background: #16a34a;
}

.pass.failed {
  background: #dc2626;
}

.arrow {
  color: #9ca3af;
  font-size: 12px;
}

.questions {
  border-top: 1px solid #f3f4f6;
}

.q-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
}

.q-row:last-child {
  border-bottom: none;
}

.q-row:hover,
.q-row.active {
  background: #eff6ff;
}

.q-title {
  flex: 1;
}

.q-score {
  min-width: 56px;
  text-align: right;
}

.status {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}

.status.done {
  background: #16a34a;
}

.status.err {
  background: #dc2626;
}

.status.pending {
  background: #d97706;
}

.link {
  color: #2563eb;
  font-size: 13px;
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

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.detail {
  padding: 16px;
  margin-top: 20px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-head h3 {
  font-size: 17px;
}

.close {
  margin-left: auto;
  background: none;
  border: none;
  font-size: 22px;
  color: #9ca3af;
  cursor: pointer;
}

.label {
  font-weight: 600;
  margin: 16px 0 8px;
}

.sub {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sub th,
.sub td {
  border: 1px solid #e5e7eb;
  padding: 6px 10px;
  text-align: left;
}

.sub th {
  background: #f9fafb;
}

.ok {
  color: #16a34a;
  font-weight: 600;
}

.fail {
  color: #dc2626;
  font-weight: 600;
}

.ai-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.stat {
  flex: 1;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  text-align: center;
}

.stat span {
  display: block;
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 4px;
}

.stat b {
  font-size: 18px;
}

.feedback {
  padding-left: 20px;
  line-height: 1.8;
  color: #374151;
}
</style>
