<!-- 学生端：在线答题页，含题目描述、Monaco Java 代码编辑器与提交 -->
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

      <!-- 已提交提示（每题限一次） -->
      <div v-if="submission" class="card submitted-banner">
        <span class="badge done">已提交</span>
        <span>状态：{{ judgeStatusText(submission.judgeStatus) }}</span>
        <span v-if="submission.score != null">得分：{{ submission.score }}</span>
        <router-link to="/student/scores">查看完整成绩 →</router-link>
      </div>

      <!-- 代码编辑器（提交后转为只读回看） -->
      <div class="card editor-card">
        <div class="label">
          {{ submission ? '我的答案（只读）' : `编写代码（${question.methodName}）` }}
        </div>
        <div ref="editorRef" class="editor"></div>
      </div>

      <div v-if="!submission" class="actions">
        <button class="btn primary" :disabled="submitting" @click="submitCode">
          {{ submitting ? '提交中...' : '提交' }}
        </button>
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
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getQuestion, getQuestionSubmission, submit } from '../../api/student'
import { createEditor } from '../../utils/monaco'
import { difficultyClass, judgeStatusText } from '../../utils/format'

const route = useRoute()
const router = useRouter()

const question = ref(null)
const submission = ref(null) // 该题已有提交（未提交时为 null）
const loading = ref(false)
const error = ref('')
const editorRef = ref(null)
const submitting = ref(false)
const submitMsg = ref('')

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

function defaultTemplate(methodName) {
  return `// 实现方法 ${methodName}（评测由后端执行）\npublic class Solution {\n    public Object ${methodName}() {\n        // 在这里编写你的代码\n        return null;\n    }\n}\n`
}

// 根据当前状态创建编辑器：未提交用初始模板可编辑；已提交用源码只读回看
function initEditor() {
  if (!editorRef.value) return
  if (editor) {
    editor.dispose()
    editor = null
  }
  const value = submission.value
    ? submission.value.sourceCode || '// 无源码'
    : defaultTemplate(question.value.methodName)
  editor = createEditor(editorRef.value, {
    value,
    readOnly: !!submission.value
  })
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
