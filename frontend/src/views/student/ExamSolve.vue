<!-- 学生端：进试卷答题页。一道题一页（分页），按考试时间窗答题，倒计时到点自动交卷，交卷后回看 -->
<template>
  <div class="page">
    <button class="back" @click="goBack">← 返回考试列表</button>

    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>

    <template v-else-if="exam">
      <!-- 考试信息 -->
      <div class="head">
        <h1>{{ exam.title }}</h1>
        <span class="badge" :class="statusClass(exam.status)">{{ statusText(exam.status) }}</span>
        <span v-if="exam.submitted" class="badge done">已交卷</span>
      </div>
      <div class="meta">
        <span>题目：{{ exam.questionCount }} 题</span>
        <span>总分：{{ exam.totalScore }}</span>
        <span>时间：{{ formatTime(exam.startTime) }} ~ {{ formatTime(exam.endTime) }}</span>
      </div>

      <!-- 状态横幅 / 倒计时 -->
      <div v-if="exam.status === 'NOT_STARTED'" class="banner notstarted">
        考试尚未开始，开始后方可作答
      </div>
      <div v-else-if="exam.status === 'ONGOING'" class="banner ongoing">
        考试进行中，剩余时间 <b>{{ countdown }}</b>
      </div>
      <div v-else class="banner ended">
        {{ exam.submitted ? '考试已结束' : '考试已结束，未交卷' }}
      </div>

      <!-- 题号跳转条 -->
      <div v-if="exam.questions.length" class="qstrip">
        <button
          v-for="(q, i) in exam.questions"
          :key="q.questionId"
          class="qnum-btn"
          :class="{ current: i === current, answered: isAnswered(i) }"
          @click="goQuestion(i)"
        >{{ i + 1 }}</button>
      </div>

      <!-- 当前题目（一道题一页） -->
      <div v-if="currentQuestion" class="card qcard">
        <div class="qhead">
          <span class="qnum">第 {{ current + 1 }} 题 · {{ currentQuestion.title }}</span>
          <span class="badge" :class="difficultyClass(currentQuestion.difficulty)">{{ currentQuestion.difficulty }}</span>
          <span class="qscore">{{ currentQuestion.score }} 分</span>
        </div>
        <div class="qdesc">{{ currentQuestion.description }}</div>

        <div v-if="randomTestCases.length" class="samples">
          <div class="label">样例测试用例（随机抽 {{ randomTestCases.length }} 条）</div>
          <table>
            <thead>
              <tr><th>名称</th><th>输入</th><th>期望输出</th></tr>
            </thead>
            <tbody>
              <tr v-for="(tc, ti) in randomTestCases" :key="ti">
                <td>{{ tc.name }}</td>
                <td><code>{{ tc.input }}</code></td>
                <td><code>{{ tc.expected }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="label">{{ editable ? `编写代码（${currentQuestion.methodName}）` : '代码' }}</div>
        <div ref="editorEl" class="editor"></div>

        <!-- 本地样例自测 -->
        <div v-if="editable" class="testbar">
          <button class="btn" :disabled="testing" @click="runTest">
            {{ testing ? '测试中...' : '测试' }}
          </button>
          <span class="test-hint">用样例用例本地跑一遍（不提交）</span>
        </div>
        <p v-if="testError" class="test-error">{{ testError }}</p>
        <div v-if="testResults" class="test-results">
          <div v-for="(r, ri) in testResults" :key="ri" class="test-row" :class="{ pass: r.passed, fail: !r.passed }">
            <span class="test-status">{{ r.passed ? '✓' : '✗' }}</span>
            <span class="test-name">{{ r.name }}</span>
            <span class="test-msg">{{ r.message }}</span>
            <span v-if="!r.passed" class="test-io">实际={{ r.actual }} 期望={{ r.expected }}</span>
          </div>
        </div>

        <p v-if="currentQuestion.myScore != null" class="my-score">
          本题得分：{{ currentQuestion.myScore }}（{{ judgeStatusText(currentQuestion.judgeStatus) }}）
        </p>
      </div>

      <!-- 底部翻页 + 交卷 -->
      <div class="footer">
        <button class="btn" :disabled="current === 0" @click="goQuestion(current - 1)">上一题</button>
        <span class="page-indicator">{{ current + 1 }} / {{ exam.questions.length }}</span>
        <button class="btn" :disabled="current === exam.questions.length - 1" @click="goQuestion(current + 1)">下一题</button>
        <button v-if="editable" class="btn primary" :disabled="submitting" @click="submitAll">
          {{ submitting ? '交卷中...' : '交卷' }}
        </button>
      </div>

      <div v-if="submitMsg" class="submit-msg">{{ submitMsg }}</div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { getExam, submitExam, reportCheat as reportCheatApi } from '../../api/student'
import { createEditor } from '../../utils/monaco'
import { runLocalTests } from '../../utils/jsRunner'
import { difficultyClass, judgeStatusText } from '../../utils/format'
import { getToken } from '../../utils/auth'

const route = useRoute()
const router = useRouter()

const exam = ref(null)
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const submitMsg = ref('')
const countdown = ref('')

// 当前题目下标（0 起）；answers 按题目下标保存学生实际输入的源码（未作答为 null）
const current = ref(0)
const answers = ref([])

const editorEl = ref(null)
let editor = null
let timer = null

// 本地样例自测状态
const testing = ref(false)
const testResults = ref(null)
const testError = ref('')

// 每次看题随机抽取的样例测试用例（最多 3 条）
const randomTestCases = ref([])

// 从数组中随机抽取最多 n 条（洗牌后取前 n）
function pickRandom(arr, n) {
  if (!arr || !arr.length) return []
  const copy = [...arr]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy.slice(0, Math.min(n, copy.length))
}

const questionCount = computed(() => (exam.value && exam.value.questions ? exam.value.questions.length : 0))
const currentQuestion = computed(() => (exam.value && exam.value.questions ? exam.value.questions[current.value] : null))

// 是否处于可作答状态：进行中 且 尚未交卷
const editable = computed(() => !!exam.value && exam.value.status === 'ONGOING' && !exam.value.submitted)

// ===== 防作弊：切屏 / 切页面检测（仅考试进行中、未交卷时生效） =====
let isAway = false

function sendCheat(eventType) {
  if (!exam.value || !editable.value) return
  reportCheatApi(exam.value.id, eventType).catch(() => {})
}

function handleVisibility() {
  if (document.hidden) {
    if (!isAway) {
      isAway = true
      sendCheat('SWITCH_TAB')
    }
  } else {
    isAway = false
  }
}

function handleBlur() {
  if (!isAway) {
    isAway = true
    sendCheat('SWITCH_TAB')
  }
}

function handleFocus() {
  isAway = false
}

// 关闭/刷新页面时用 keepalive 请求上报「切页面」（普通 fetch 在 unload 时会被中断）
function handleUnload() {
  if (!exam.value || !editable.value) return
  const token = getToken()
  fetch(`/api/student/exams/${exam.value.id}/cheat-event`, {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ eventType: 'LEAVE_PAGE' })
  }).catch(() => {})
}

// 离开答题页（点返回、切到别的菜单）→ 记一次「切页面」
onBeforeRouteLeave(() => {
  sendCheat('LEAVE_PAGE')
})

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getExam(route.params.id)
    exam.value = res.data
    answers.value = exam.value.questions.map(() => null)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }

  if (exam.value) {
    await nextTick()
    initEditor()
    if (exam.value.status === 'ONGOING' && !exam.value.submitted) {
      startCountdown()
    }
  }

  // 注册防作弊监听
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('blur', handleBlur)
  window.addEventListener('focus', handleFocus)
  window.addEventListener('beforeunload', handleUnload)
})

onBeforeUnmount(() => {
  disposeEditor()
  if (timer) clearInterval(timer)
  // 移除防作弊监听
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('blur', handleBlur)
  window.removeEventListener('focus', handleFocus)
  window.removeEventListener('beforeunload', handleUnload)
})

function goBack() {
  disposeEditor()
  if (timer) clearInterval(timer)
  router.push('/student/home')
}

function defaultTemplate(methodName) {
  return `// 实现方法 ${methodName}（评测由后端执行）\npublic class Solution {\n    public Object ${methodName}() {\n        // 在这里编写你的代码\n        return null;\n    }\n}\n`
}

function draftKey(questionId) {
  return `codejudge_draft_${exam.value.id}_${questionId}`
}

function loadDraft(questionId) {
  return localStorage.getItem(draftKey(questionId)) || ''
}

function saveDraft(questionId, code) {
  localStorage.setItem(draftKey(questionId), code)
}

function clearDrafts() {
  if (!exam.value) return
  exam.value.questions.forEach(q => localStorage.removeItem(draftKey(q.questionId)))
}

function initEditor() {
  disposeEditor()
  const q = currentQuestion.value
  if (!q) return
  // 每次进入 / 切换题目时，随机抽取最多 3 条样例测试用例展示
  randomTestCases.value = pickRandom(q.testCases, 3)
  if (!editorEl.value) return
  const i = current.value
  const value = editable.value
    ? (answers.value[i] ?? loadDraft(q.questionId) ?? defaultTemplate(q.methodName))
    : (q.sourceCode || '// 无源码')
  editor = createEditor(editorEl.value, {
    value,
    readOnly: !editable.value,
    onChange: code => {
      if (editable.value) {
        answers.value[i] = code
        saveDraft(q.questionId, code)
      }
    }
  })
}

function disposeEditor() {
  if (editor) {
    try {
      editor.dispose()
    } catch (e) {
      // 忽略 dispose 异常，避免返回/切换题目时卡住
    }
    editor = null
  }
}

function goQuestion(i) {
  if (i < 0 || i >= questionCount.value || i === current.value) return
  current.value = i
  testResults.value = null
  testError.value = ''
  nextTick(() => initEditor())
}

// 本地样例自测：读当前编辑器代码，在浏览器里跑一遍样例用例
async function runTest() {
  const q = currentQuestion.value
  if (!q || !editor) return
  testError.value = ''
  testResults.value = null
  const code = editor.getValue()
  if (!code || !code.trim()) {
    testError.value = '请先编写代码'
    return
  }
  if (!randomTestCases.value.length) {
    testError.value = '本题暂无样例测试用例'
    return
  }
  testing.value = true
  try {
    const res = await runLocalTests(code, q.methodName, randomTestCases.value)
    if (res.compileError) {
      testError.value = res.compileError
      testResults.value = null
    } else {
      testResults.value = res.results
    }
  } finally {
    testing.value = false
  }
}

// 是否真正作答过（有输入、且不是默认模板）
function isAnswered(i) {
  const q = exam.value && exam.value.questions ? exam.value.questions[i] : null
  if (!q) return false
  const code = answers.value[i]
  if (code == null || !code.trim()) return false
  return code.trim() !== defaultTemplate(q.methodName).trim()
}

// 交卷时把「空白 / 还是默认模板」的题目当作未作答
function normalizeAnswer(q, code) {
  if (!code || !code.trim()) return ''
  if (code.trim() === defaultTemplate(q.methodName).trim()) return ''
  return code
}

function startCountdown() {
  tick()
  timer = setInterval(tick, 1000)
}

function tick() {
  if (!exam.value || !exam.value.endTime) return
  const remaining = new Date(exam.value.endTime).getTime() - Date.now()
  if (remaining <= 0) {
    countdown.value = '00:00:00'
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    // 到点自动交卷
    if (editable.value && !submitting.value) {
      submitAll()
    }
    return
  }
  countdown.value = formatRemaining(remaining)
}

function formatRemaining(ms) {
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = n => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

async function submitAll() {
  if (!exam.value || submitting.value) return
  submitting.value = true
  submitMsg.value = ''
  const payload = exam.value.questions.map((q, i) => {
    const raw = answers.value[i] ?? loadDraft(q.questionId) ?? ''
    return { questionId: q.questionId, sourceCode: normalizeAnswer(q, raw) }
  })
  try {
    const res = await submitExam(exam.value.id, { answers: payload })
    submitMsg.value = `交卷成功（已作答 ${res.data.answeredCount}/${res.data.totalCount} 题）`
    // 交卷后回看：重新拉详情，清草稿，切换为只读
    clearDrafts()
    const detail = await getExam(exam.value.id)
    exam.value = detail.data
    current.value = 0
    answers.value = exam.value.questions.map(() => null)
    await nextTick()
    initEditor()
  } catch (e) {
    submitMsg.value = e.message || '交卷失败'
  } finally {
    submitting.value = false
  }
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

function formatTime(s) {
  return s ? s.replace('T', ' ') : '—'
}
</script>

<style scoped>
.page h1 {
  font-size: 24px;
}

.back {
  background: none;
  border: none;
  color: #2563eb;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
  margin-bottom: 16px;
}

.hint {
  color: #6b7280;
  padding: 20px 0;
}

.hint.error {
  color: #dc2626;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
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
  margin-bottom: 16px;
}

.banner {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 16px;
}

.banner.notstarted {
  background: #eff6ff;
  color: #1d4ed8;
}

.banner.ongoing {
  background: #f0fdf4;
  color: #16a34a;
}

.banner.ended {
  background: #f3f4f6;
  color: #6b7280;
}

.banner b {
  font-size: 16px;
}

/* 题号跳转条 */
.qstrip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.qnum-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  font-size: 14px;
}

.qnum-btn.current {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.qnum-btn.answered {
  border-color: #16a34a;
  color: #16a34a;
}

.qnum-btn.answered.current {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.qcard {
  padding: 0;
  overflow: hidden;
}

.qhead {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 8px;
}

.qnum {
  font-weight: 600;
  font-size: 16px;
}

.qscore {
  margin-left: auto;
  color: #6b7280;
  font-size: 13px;
}

.qdesc {
  white-space: pre-wrap;
  color: #374151;
  line-height: 1.6;
  padding: 0 16px 12px;
}

.label {
  font-weight: 600;
  padding: 0 16px 8px;
}

.samples {
  padding: 0 16px 12px;
}

.samples .label {
  padding: 0 0 8px;
}

.samples table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.samples th,
.samples td {
  border: 1px solid #e5e7eb;
  padding: 6px 10px;
  text-align: left;
}

.samples th {
  background: #f9fafb;
}

.editor {
  height: 260px;
  border-top: 1px solid #e5e7eb;
}

.my-score {
  padding: 8px 16px 12px;
  color: #16a34a;
  font-size: 13px;
}

/* 本地样例自测 */
.testbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
}

.test-hint {
  color: #9ca3af;
  font-size: 12px;
}

.test-error {
  padding: 0 16px 10px;
  color: #dc2626;
  font-size: 13px;
}

.test-results {
  padding: 0 16px 12px;
}

.test-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}

.test-row.pass {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.test-row.fail {
  background: #fef2f2;
  border-color: #fecaca;
}

.test-status {
  font-weight: 700;
}

.test-row.pass .test-status {
  color: #16a34a;
}

.test-row.fail .test-status {
  color: #dc2626;
}

.test-name {
  color: #374151;
}

.test-msg {
  color: #6b7280;
}

.test-io {
  color: #6b7280;
}

/* 底部翻页 + 交卷 */
.footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.page-indicator {
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

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  margin-left: auto;
}

.submit-msg {
  color: #16a34a;
  padding: 8px 0;
}
</style>
