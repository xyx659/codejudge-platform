<!-- 学生端：进试卷答题页。按考试时间窗答题，倒计时到点自动交卷，交卷后回看 -->
<template>
  <div class="page">
    <button class="back" @click="router.push('/student/home')">← 返回考试列表</button>

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

      <!-- 题目列表（每题一个编辑器） -->
      <div v-for="(q, i) in exam.questions" :key="q.questionId" class="card qcard">
        <div class="qhead">
          <span class="qnum">{{ i + 1 }}. {{ q.title }}</span>
          <span class="badge" :class="difficultyClass(q.difficulty)">{{ q.difficulty }}</span>
          <span class="qscore">{{ q.score }} 分</span>
        </div>
        <div class="qdesc">{{ q.description }}</div>

        <div v-if="q.testCases && q.testCases.length" class="samples">
          <div class="label">样例测试用例</div>
          <table>
            <thead>
              <tr><th>名称</th><th>输入</th><th>期望输出</th></tr>
            </thead>
            <tbody>
              <tr v-for="(tc, ti) in q.testCases" :key="ti">
                <td>{{ tc.name }}</td>
                <td><code>{{ tc.input }}</code></td>
                <td><code>{{ tc.expected }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="label">{{ editable ? `编写代码（${q.methodName}）` : '代码' }}</div>
        <div :ref="el => setEditorEl(el, i)" class="editor"></div>
        <p v-if="q.myScore != null" class="my-score">
          本题得分：{{ q.myScore }}（{{ judgeStatusText(q.judgeStatus) }}）
        </p>
      </div>

      <!-- 交卷 -->
      <div v-if="editable" class="actions">
        <button class="btn primary" :disabled="submitting" @click="submitAll">
          {{ submitting ? '交卷中...' : '交卷' }}
        </button>
      </div>

      <div v-if="submitMsg" class="submit-msg">{{ submitMsg }}</div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExam, submitExam } from '../../api/student'
import { createEditor } from '../../utils/monaco'
import { difficultyClass, judgeStatusText } from '../../utils/format'

const route = useRoute()
const router = useRouter()

const exam = ref(null)
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const submitMsg = ref('')
const countdown = ref('')

const editorEls = ref([])
const editors = ref([])

let timer = null

// 是否处于可作答状态：进行中 且 尚未交卷
const editable = computed(() => !!exam.value && exam.value.status === 'ONGOING' && !exam.value.submitted)

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getExam(route.params.id)
    exam.value = res.data
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }

  if (exam.value) {
    await nextTick()
    initEditors()
    if (exam.value.status === 'ONGOING' && !exam.value.submitted) {
      startCountdown()
    }
  }
})

onBeforeUnmount(() => {
  disposeEditors()
  if (timer) clearInterval(timer)
})

function setEditorEl(el, i) {
  editorEls.value[i] = el
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

function initEditors() {
  disposeEditors()
  if (!exam.value) return
  exam.value.questions.forEach((q, i) => {
    const el = editorEls.value[i]
    if (!el) return
    const value = exam.value.submitted
      ? q.sourceCode || '// 无源码'
      : loadDraft(q.questionId) || defaultTemplate(q.methodName)
    const editor = createEditor(el, {
      value,
      readOnly: !editable.value,
      onChange: code => {
        if (editable.value) saveDraft(q.questionId, code)
      }
    })
    editors.value.push(editor)
  })
}

function disposeEditors() {
  editors.value.forEach(e => e.dispose())
  editors.value = []
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
    if (timer) clearInterval(timer)
    timer = null
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
  const answers = exam.value.questions.map((q, i) => {
    const editor = editors.value[i]
    return { questionId: q.questionId, sourceCode: editor ? editor.getValue() : '' }
  })
  try {
    const res = await submitExam(exam.value.id, { answers })
    submitMsg.value = `交卷成功（已作答 ${res.data.answeredCount}/${res.data.totalCount} 题）`
    // 交卷后回看：重新拉详情，清草稿，切换为只读
    clearDrafts()
    const detail = await getExam(exam.value.id)
    exam.value = detail.data
    await nextTick()
    initEditors()
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

.hint.error,
.error {
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
  height: 240px;
  border-top: 1px solid #e5e7eb;
}

.my-score {
  padding: 8px 16px 12px;
  color: #16a34a;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.btn {
  padding: 8px 20px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  font-size: 14px;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.submit-msg {
  color: #16a34a;
  padding: 8px 0;
}
</style>
