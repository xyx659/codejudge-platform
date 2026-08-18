<!-- 学生端：在线答题页，含题目描述、Monaco 代码编辑器、本地样例自测与提交 -->
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
        <span>语言：{{ question.language }}</span>
        <span>方法名：{{ question.methodName }}</span>
        <span v-if="question.tags && question.tags.length">
          标签：{{ question.tags.join('、') }}
        </span>
      </div>

      <div class="card desc">
        <div class="label">题目描述</div>
        <p class="desc-text">{{ question.description }}</p>
      </div>

      <div class="card">
        <div class="label">样例测试用例</div>
        <table v-if="question.testCases && question.testCases.length" class="samples">
          <thead>
            <tr><th>名称</th><th>输入</th><th>期望输出</th></tr>
          </thead>
          <tbody>
            <tr v-for="(tc, i) in question.testCases" :key="i">
              <td>{{ tc.name }}</td>
              <td><code>{{ tc.input }}</code></td>
              <td><code>{{ tc.expected }}</code></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="hint">本题暂无样例用例</p>
      </div>

      <!-- 代码编辑器 -->
      <div class="card editor-card">
        <div class="label">编写代码（{{ question.methodName }}）</div>
        <div ref="editorRef" class="editor"></div>
      </div>

      <div class="actions">
        <button class="btn" :disabled="testing" @click="selfTest">
          {{ testing ? '自测中...' : '样例自测' }}
        </button>
        <button class="btn primary" :disabled="submitting" @click="submitCode">
          {{ submitting ? '提交中...' : '提交' }}
        </button>
      </div>

      <!-- 本地自测结果 -->
      <div v-if="testResult" class="card result-card">
        <div class="label">样例自测结果</div>
        <p v-if="testResult.error" class="error">{{ testResult.error }}</p>
        <template v-else>
          <p class="summary">
            通过 <b>{{ passedCount }}</b> / {{ testResult.results.length }} 个样例
          </p>
          <div v-for="r in testResult.results" :key="r.name" class="case" :class="r.passed ? 'ok' : 'fail'">
            <div class="case-head">
              <span class="case-name">{{ r.name }}</span>
              <span class="case-verdict">{{ r.message }}</span>
              <span class="case-time">{{ r.durationMs }}ms</span>
            </div>
            <div class="case-io">
              <span>输入：{{ r.input }}</span>
              <span>期望：{{ r.expected }}</span>
              <span>实际：{{ r.actual || '（无输出）' }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- 提交结果 -->
      <div v-if="submitMsg" class="card submit-msg">
        {{ submitMsg }}
        <router-link to="/student/scores">去查看成绩 →</router-link>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getQuestion, submit } from '../../api/student'
import { createEditor } from '../../utils/monaco'
import { runLocalTests } from '../../utils/jsRunner'
import { difficultyClass } from '../../utils/format'

const route = useRoute()
const router = useRouter()

const question = ref(null)
const loading = ref(false)
const error = ref('')
const editorRef = ref(null)
const testResult = ref(null)
const testing = ref(false)
const submitting = ref(false)
const submitMsg = ref('')

let editor = null

const passedCount = computed(() => {
  if (!testResult.value || !testResult.value.results) return 0
  return testResult.value.results.filter((r) => r.passed).length
})

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getQuestion(route.params.id)
    question.value = res.data
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    // 先结束 loading，触发 v-else-if="question" 分支渲染出编辑器容器
    loading.value = false
  }

  // 题目加载成功且 DOM 渲染出编辑器容器后，再创建 Monaco 实例并填入初始模板
  if (question.value) {
    await nextTick()
    if (editorRef.value) {
      editor = createEditor(editorRef.value, {
        value: defaultTemplate(question.value.methodName)
      })
    }
  }
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})

function defaultTemplate(methodName) {
  return `// 实现函数 ${methodName}，参数从输入按空格分隔后传入\nfunction ${methodName}() {\n  // 在这里编写你的代码\n  return null\n}\n`
}

async function selfTest() {
  // 编辑器未就绪（多为页面刷新后 Monaco 未挂载）或代码为空，都要给出明确提示
  const code = editor ? editor.getValue() : ''
  if (!editor || !code.trim()) {
    testResult.value = { error: '编辑器尚未就绪或代码为空，请刷新页面后重试' }
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await runLocalTests(
      code,
      question.value.methodName,
      question.value.testCases || []
    )
  } catch (e) {
    // 兜底：把任何运行时异常都显示出来，而不是无声失败
    testResult.value = { error: (e && e.message) || String(e) }
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

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 16px;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
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

.samples {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.samples th,
.samples td {
  border: 1px solid #e5e7eb;
  padding: 8px 10px;
  text-align: left;
}

.samples th {
  background: #f9fafb;
  font-weight: 600;
}

.editor-card {
  padding: 0;
  overflow: hidden;
}

.editor-card .label {
  padding: 16px 16px 8px;
}

.editor {
  height: 360px;
  border-top: 1px solid #e5e7eb;
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

.result-card .summary {
  margin-bottom: 12px;
  font-size: 15px;
}

.case {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
}

.case.ok {
  border-left: 4px solid #16a34a;
}

.case.fail {
  border-left: 4px solid #dc2626;
}

.case-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.case-name {
  font-weight: 600;
}

.case-verdict {
  color: #374151;
}

.case-time {
  margin-left: auto;
  color: #9ca3af;
  font-size: 12px;
}

.case-io {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 13px;
}

.submit-msg {
  color: #16a34a;
}

.submit-msg a {
  margin-left: 12px;
}
</style>
