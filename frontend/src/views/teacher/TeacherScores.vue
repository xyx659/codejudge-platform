<!-- 教师端：学生成绩（按考试查看逐人得分，点开看答卷与 AI 评审） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>学生成绩</h1>
        <p>按考试查看学生得分，点开查看试卷答案、测试用例与 AI 评审</p>
      </div>
      <label class="exam-select">
        <span>选择考试</span>
        <select v-model="examId" @change="onExamChange">
          <option value="" disabled>请选择考试</option>
          <option v-for="e in exams" :key="e.id" :value="e.id">{{ e.title }}</option>
        </select>
      </label>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section class="toolbar">
      <input
        v-model.trim="keyword"
        type="text"
        placeholder="搜索姓名 / 学号 / 账号"
        @keyup.enter="applyFilters"
      />
      <select v-model="className" @change="applyFilters">
        <option value="">全部班级</option>
        <option v-for="c in classes" :key="c" :value="c">{{ c }}</option>
      </select>
      <select v-model="passed" @change="applyFilters">
        <option value="">全部（及格/不及格）</option>
        <option value="true">已及格</option>
        <option value="false">未及格</option>
      </select>
      <button type="button" class="secondary" @click="applyFilters">查询</button>
    </section>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>学号</th>
            <th>姓名</th>
            <th>班级</th>
            <th>得分</th>
            <th>状态</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="students.length === 0">
            <td colspan="6" class="empty-cell">暂无学生</td>
          </tr>
          <template v-else>
            <template v-for="s in students" :key="s.studentId">
              <tr>
                <td>{{ s.studentNo || '-' }}</td>
                <td>{{ s.name }}</td>
                <td>{{ s.className || '-' }}</td>
                <td>{{ s.submitted ? `${s.achievedScore} / ${s.fullScore}` : '-' }}</td>
                <td>
                  <span
                    class="status-badge"
                    :class="s.submitted ? (s.passed ? 'passed' : 'failed') : 'absent'"
                  >
                    {{ s.submitted ? (s.passed ? '已及格' : '未及格') : '未交卷' }}
                  </span>
                </td>
                <td>
                  <button type="button" @click="toggleAnswers(s)">
                    {{ expandedId === s.studentId ? '收起' : '查看答卷' }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedId === s.studentId" class="answer-row">
                <td colspan="6">
                  <div v-if="answersLoading" class="answer-hint">答卷加载中...</div>
                  <div v-else-if="answers.length === 0" class="answer-hint">暂无答卷</div>
                  <div v-else class="answer-list">
                    <div
                      v-for="a in answers"
                      :key="a.questionId"
                      class="answer-item"
                      @click="openDetail(a)"
                    >
                      <span class="answer-title">{{ a.questionTitle }}</span>
                      <span class="answer-status" :class="statusClass(a.judgeStatus)">
                        {{ statusText[a.judgeStatus] || a.judgeStatus }}
                      </span>
                      <span class="answer-score">{{ a.score == null ? '—' : a.score }} / {{ a.fullScore }} 分</span>
                      <span class="answer-link">查看 →</span>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </template>
        </tbody>
      </table>
    </section>

    <div class="pagination">
      <button type="button" :disabled="page <= 0 || loading" @click="previousPage">上一页</button>
      <span>第 {{ page + 1 }} / {{ pageCount }} 页</span>
      <button type="button" :disabled="page >= pageCount - 1 || loading" @click="nextPage">下一页</button>
      <span class="total">共 {{ total }} 条</span>
    </div>

    <!-- 答卷详情弹窗（源码 + 测试用例 + AI 评审） -->
    <div v-if="detail" class="modal-backdrop" @click.self="closeDetail">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ detail.questionTitle }}</h2>
          <button type="button" class="close-button" @click="closeDetail">关闭</button>
        </header>

        <div class="detail-score">
          得分 {{ detail.score == null ? '—' : detail.score }} / {{ detail.fullScore }} 分 ·
          {{ statusText[detail.judgeStatus] || detail.judgeStatus }}
        </div>

        <div class="label">学生源码</div>
        <pre v-if="detail.sourceCode" class="code">{{ detail.sourceCode }}</pre>
        <p v-else class="hint">未作答</p>

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
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStudentAnswers, listClasses, listExams, listStudentScores } from '../../api/teacher'

const statusText = {
  RUN_COMPLETED: '完成',
  COMPILE_ERROR: '编译错误',
  TIMEOUT: '超时',
  UNANSWERED: '未作答',
  PENDING: '评测中'
}

const exams = ref([])
const examId = ref('')
const classes = ref([])

const students = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const keyword = ref('')
const className = ref('')
const passed = ref('')
const loading = ref(false)
const error = ref('')

const expandedId = ref(null)
const answers = ref([])
const answersLoading = ref(false)

const detail = ref(null)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function statusClass(status) {
  if (status === 'RUN_COMPLETED') return 'done'
  if (status === 'COMPILE_ERROR' || status === 'TIMEOUT') return 'err'
  return 'pending'
}

async function loadExams() {
  try {
    const res = await listExams({ size: 1000 })
    exams.value = res.data.list || []
    if (exams.value.length > 0 && !examId.value) {
      examId.value = exams.value[0].id
      await loadStudents()
    }
  } catch (e) {
    error.value = e.message || '考试列表加载失败'
  }
}

async function loadClasses() {
  try {
    const res = await listClasses()
    classes.value = res.data || []
  } catch (e) {
    // 班级加载失败不阻塞主流程
  }
}

async function loadStudents() {
  if (!examId.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await listStudentScores(examId.value, {
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      className: className.value,
      passed: passed.value
    })
    students.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    error.value = e.message || '学生成绩加载失败'
  } finally {
    loading.value = false
  }
}

function onExamChange() {
  page.value = 0
  expandedId.value = null
  answers.value = []
  detail.value = null
  loadStudents()
}

function applyFilters() {
  page.value = 0
  expandedId.value = null
  answers.value = []
  loadStudents()
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
    loadStudents()
  }
}

function nextPage() {
  if (page.value < pageCount.value - 1) {
    page.value += 1
    loadStudents()
  }
}

async function toggleAnswers(s) {
  if (expandedId.value === s.studentId) {
    expandedId.value = null
    answers.value = []
    return
  }
  expandedId.value = s.studentId
  answersLoading.value = true
  answers.value = []
  try {
    const res = await getStudentAnswers(examId.value, s.studentId)
    answers.value = res.data || []
  } catch (e) {
    error.value = e.message || '答卷加载失败'
  } finally {
    answersLoading.value = false
  }
}

function openDetail(a) {
  detail.value = a
}

function closeDetail() {
  detail.value = null
}

onMounted(() => {
  loadClasses()
  loadExams()
})
</script>

<style scoped>
.page {
  max-width: 1100px;
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  margin-bottom: 14px;
}

.toolbar input,
.toolbar select,
.exam-select select,
button {
  min-height: 36px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font: inherit;
}

.toolbar input {
  padding: 0 10px;
  min-width: 200px;
}

.toolbar select {
  padding: 0 10px;
}

button {
  padding: 0 12px;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.secondary {
  background: #fff;
  color: #059669;
  border-color: #a7f3d0;
}

.table-shell {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

table {
  width: 100%;
  min-width: 760px;
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

.actions-column {
  width: 110px;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.status-badge.passed {
  background: #16a34a;
}

.status-badge.failed {
  background: #dc2626;
}

.status-badge.absent {
  background: #9ca3af;
}

.empty-cell {
  height: 140px;
  text-align: center;
  color: #6b7280;
}

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  color: #4b5563;
}

.pagination .total {
  margin-left: auto;
}

/* 展开的答卷列表 */
.answer-row td {
  background: #f9fafb;
}

.answer-list {
  display: grid;
  gap: 6px;
}

.answer-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}

.answer-item:hover {
  background: #ecfdf5;
}

.answer-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.answer-status {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: #fff;
}

.answer-status.done {
  background: #16a34a;
}

.answer-status.err {
  background: #dc2626;
}

.answer-status.pending {
  background: #d97706;
}

.answer-score {
  min-width: 80px;
  text-align: right;
  color: #059669;
  font-weight: 600;
}

.answer-link {
  color: #059669;
  font-size: 13px;
}

.answer-hint {
  padding: 16px;
  text-align: center;
  color: #9ca3af;
}

/* 详情弹窗 */
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.48);
}

.modal {
  width: min(720px, 100%);
  max-height: 90vh;
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.modal-header h2 {
  font-size: 18px;
}

.close-button {
  border: 0;
  background: transparent;
  color: #6b7280;
  font-size: 20px;
}

.detail-score {
  color: #059669;
  font-weight: 600;
  margin-bottom: 12px;
}

.label {
  font-weight: 600;
  margin: 16px 0 8px;
}

.code {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.5;
}

.hint {
  color: #6b7280;
  padding: 8px 0;
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
