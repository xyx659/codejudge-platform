<!-- 学生端：在线答题页，LeetCode 式左右两栏（左：题目描述/样例；右：编辑器 + 测试/提交） -->
<template>
  <div class="page">
    <button class="back" @click="router.push('/student/home')">← 返回题目列表</button>

    <!-- 加载 / 错误 -->
    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>

    <template v-else-if="question">
      <!-- 题目信息 -->
      <div class="head">
        <h1>{{ question.title }}</h1>
        <span class="badge" :class="difficultyClass(question.difficulty)">
          {{ question.difficulty }}
        </span>
      </div>
      <div class="meta">
        <span>方法名：{{ question.methodName }}</span>
        <span v-if="question.tags && question.tags.length">
          标签：{{ question.tags.join('、') }}
        </span>
      </div>

      <div class="workspace">
        <!-- 左栏：题目描述 + 样例 + 已提交提示 -->
        <div class="pane pane-left">
          <div class="card">
            <div class="label">题目描述</div>
            <p class="desc-text">{{ question.description }}</p>
          </div>

          <div class="card">
            <div class="label">样例测试用例</div>
            <div v-if="question.testCases && question.testCases.length" class="examples">
              <div v-for="(tc, i) in question.testCases" :key="i" class="example">
                <div class="example-head">
                  <span class="example-no">示例 {{ i + 1 }}</span>
                  <span v-if="tc.name" class="example-name">{{ tc.name }}</span>
                </div>
                <div class="example-io">
                  <div class="io-row"><span class="io-k">输入</span><pre class="io-v">{{ tc.input }}</pre></div>
                  <div class="io-row"><span class="io-k">输出</span><pre class="io-v">{{ tc.expected }}</pre></div>
                </div>
              </div>
            </div>
            <p v-else class="hint">本题暂无样例用例</p>
          </div>

          <div v-if="submission" class="card submitted-banner">
            <span class="badge done">已提交</span>
            <span>状态：{{ judgeStatusText(submission.judgeStatus) }}</span>
            <span v-if="submission.score != null">得分：{{ submission.score }}</span>
            <router-link to="/student/scores">查看完整成绩 →</router-link>
          </div>
        </div>

        <!-- 右栏：编辑器 + 测试/提交按钮 + 结果 -->
        <div class="pane pane-right">
          <div class="card editor-card">
            <div class="editor-toolbar">
              <span class="editor-title">
                {{ submission ? '我的答案（只读）' : `编写代码（${question.methodName}）` }}
              </span>
              <div v-if="!submission" class="editor-btns">
                <button class="btn" :disabled="testing" @click="runTest">
                  {{ testing ? '测试中...' : '测试' }}
                </button>
                <button class="btn primary" :disabled="submitting" @click="submitCode">
                  {{ submitting ? '提交中...' : '提交' }}
                </button>
              </div>
            </div>
            <div ref="editorRef" class="editor"></div>
          </div>

          <p v-if="testError" class="test-error">{{ testError }}</p>
          <div v-if="testResults && testResults.length" class="test-results">
            <p v-if="failedResults.length === 0" class="all-pass">✓ 全部通过（{{ testResults.length }} 个用例）</p>
            <template v-else>
              <div v-for="(r, ri) in failedResults" :key="ri" class="test-row fail">
                <span class="test-status">✗</span>
                <span class="test-name">{{ r.name }}</span>
                <span class="test-msg">{{ r.message }}</span>
                <span class="test-io">实际={{ r.actual }} 期望={{ r.expected }}</span>
              </div>
            </template>
          </div>

          <div v-if="submitMsg" class="card submit-msg">
            {{ submitMsg }}
            <router-link to="/student/scores">去查看成绩 →</router-link>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getQuestion, getQuestionSubmission, submit, runCode } from '../../api/student'
import { createEditor } from '../../utils/monaco'
import { difficultyClass, judgeStatusText } from '../../utils/format'
import { defaultTemplate } from '../../utils/templates'

const route = useRoute()
const router = useRouter()

const question = ref(null)
const submission = ref(null) // 该题已有提交（未提交时为 null）
const loading = ref(false)
const error = ref('')
const editorRef = ref(null)
const submitting = ref(false)
const submitMsg = ref('')
const testing = ref(false)
const testResults = ref(null)
const testError = ref('')

// 只显示未通过的用例；全部通过时展示「全部通过」
const failedResults = computed(() => (testResults.value || []).filter((r) => !r.passed))

let editor = null

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getQuestion(route.params.id)
    question.value = res.data
    // 查询该题是否已提交过（用于「每题一次」与提交后回看）
    const sub = await getQuestionSubmission(route.params.id)
    submission.value = sub.data
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    // 先结束 loading，触发 v-else-if="question" 分支渲染出编辑器容器
    loading.value = false
  }

  // 题目加载成功且 DOM 渲染出编辑器容器后，再创建 Monaco 实例
  if (question.value) {
    await nextTick()
    initEditor()
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})

// 根据当前状态创建编辑器：未提交用初始模板可编辑；已提交用源码只读回看
function initEditor() {
  if (!editorRef.value) return
  if (editor) {
    editor.dispose()
    editor = null
  }
  const value = submission.value
    ? submission.value.sourceCode || '// 无源码'
    : defaultTemplate(question.value)
  editor = createEditor(editorRef.value, {
    value,
    readOnly: !!submission.value,
    language: question.value.language
  })
}

async function runTest() {
  const q = question.value
  if (!q || !editor) return
  testError.value = ''
  testResults.value = null
  const code = editor.getValue()
  if (!code || !code.trim()) {
    testError.value = '请先编写代码'
    return
  }
  if (!q.testCases || !q.testCases.length) {
    testError.value = '本题暂无样例测试用例'
    return
  }
  testing.value = true
  try {
    const res = await runCode({ questionId: q.id, sourceCode: code, testCases: q.testCases })
    const data = res.data
    if (data.compileError) {
      testError.value = data.compileError
      testResults.value = null
    } else {
      testResults.value = data.results
    }
  } catch (e) {
    testError.value = e.message || '测试失败'
  } finally {
    testing.value = false
  }
}

async function submitCode() {
  const code = editor ? editor.getValue() : ''
  if (!code.trim()) {
    submitMsg.value = '代码为空，无法提交'
    return
  }
  submitting.value = true
  submitMsg.value = ''
  try {
    const res = await submit({
      questionId: question.value.id,
      sourceCode: code
    })
    submitMsg.value = `提交成功（编号 #${res.data.submissionId}），评测中...`
    // 提交成功后立即刷新为「已提交」只读状态，回看自己的答案
    const sub = await getQuestionSubmission(question.value.id)
    submission.value = sub.data
    await nextTick()
    initEditor()
  } catch (e) {
    submitMsg.value = e.message || '提交失败'
  } finally {
    submitting.value = false
  }
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

.badge.done {
  background: #16a34a;
}

.submitted-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  background: #f0fdf4;
  border-color: #bbf7d0;
  font-size: 14px;
}

.submitted-banner a {
  margin-left: auto;
  color: #2563eb;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 16px;
}

/* LeetCode 式左右两栏 */
.workspace {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.pane {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pane-left {
  flex: 0 0 42%;
  max-width: 46%;
}

.pane-right {
  flex: 1;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 0;
}

.card .label {
  font-weight: 600;
  margin-bottom: 10px;
}

.desc-text {
  white-space: pre-wrap;
  color: #374151;
  line-height: 1.6;
}

.examples {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.example {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #f9fafb;
}

.example-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 12px;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}

.example-no {
  font-weight: 700;
  font-size: 13px;
  color: #1f2937;
}

.example-name {
  font-size: 12px;
  color: #6b7280;
}

.example-io {
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.io-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.io-k {
  flex: 0 0 auto;
  color: #6b7280;
  font-size: 13px;
}

.io-v {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-all;
}

.editor-card {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.editor-title {
  font-weight: 600;
}

.editor-btns {
  display: flex;
  gap: 10px;
}

.editor {
  height: calc(100vh - 240px);
  min-height: 480px;
}

.test-error {
  color: #dc2626;
  font-size: 13px;
  padding: 0 4px;
}

.test-results {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.all-pass {
  color: #16a34a;
  font-size: 14px;
  font-weight: 600;
  padding: 4px;
}

.test-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
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
}

.submit-msg a {
  margin-left: 12px;
}

/* 窄屏降级为上下堆叠 */
@media (max-width: 900px) {
  .workspace {
    flex-direction: column;
  }

  .pane-left {
    flex: none;
    max-width: none;
    width: 100%;
  }

  .editor {
    height: 480px;
  }
}
</style>
