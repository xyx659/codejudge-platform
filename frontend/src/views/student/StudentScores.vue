<!-- 学生端：我的成绩，展示提交记录列表，点开查看得分、用例结果与 AI 反馈 -->
<template>
  <div class="page">
    <h1>我的成绩</h1>
    <p class="desc">查看历史提交的得分与 AI 评审反馈</p>

    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>
    <p v-else-if="submissions.length === 0" class="hint">暂无提交记录，去考试首页做一道题吧</p>

    <template v-else>
      <table class="table">
        <thead>
          <tr>
            <th>题目标题</th>
            <th>状态</th>
            <th>得分</th>
            <th>提交时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="s in submissions"
            :key="s.id"
            class="row"
            :class="{ active: selectedId === s.id }"
            @click="openDetail(s.id)"
          >
            <td>{{ s.questionTitle || '（题目已删除）' }}</td>
            <td>
              <span class="status" :class="statusClass(s.judgeStatus)">
                {{ judgeStatusText(s.judgeStatus) }}
              </span>
            </td>
            <td>{{ s.score == null ? '—' : s.score }}</td>
            <td>{{ formatTime(s.createdAt) }}</td>
            <td><span class="link">查看 →</span></td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="totalPages > 1">
        <button class="btn" :disabled="page <= 0" @click="changePage(page - 1)">上一页</button>
        <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
        <button class="btn" :disabled="page >= totalPages - 1" @click="changePage(page + 1)">下一页</button>
      </div>

      <!-- 成绩详情 -->
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
import { getSubmissionResult, listSubmissions } from '../../api/student'
import { judgeStatusText } from '../../utils/format'

const submissions = ref([])
const page = ref(0)
const size = ref(10)
const total = ref(0)
const totalPages = ref(1)
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
    const res = await listSubmissions({ page: page.value, size: size.value })
    submissions.value = res.data.list || []
    total.value = res.data.total || 0
    totalPages.value = res.data.size ? Math.ceil(total.value / res.data.size) : 1
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  load()
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

.hint.error,
.error {
  color: #dc2626;
}

.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  font-size: 14px;
}

.table th,
.table td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid #f3f4f6;
}

.table th {
  background: #f9fafb;
  font-weight: 600;
}

.row {
  cursor: pointer;
}

.row:hover,
.row.active {
  background: #eff6ff;
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
}

.pagination {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 16px 0;
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
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
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

.score {
  font-size: 20px;
  font-weight: 700;
  color: #2563eb;
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
