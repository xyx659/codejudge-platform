<!-- 教师端：题库管理（列表、搜索、增删改、发布、测试用例维护） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>题库管理</h1>
        <p>上传题目、维护测试用例与分类</p>
      </div>
      <button type="button" class="primary" @click="openCreate">新增题目</button>
    </header>

    <section class="toolbar">
      <input
        v-model.trim="keyword"
        type="text"
        placeholder="搜索标题或描述"
        @keyup.enter="applyFilters"
      />
      <select v-model="difficulty" @change="applyFilters">
        <option value="">全部难度</option>
        <option v-for="d in difficultyOptions" :key="d" :value="d">{{ d }}</option>
      </select>
      <select v-model="categoryId" @change="applyFilters">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <input
        v-model.trim="tag"
        type="text"
        placeholder="按标签筛选"
        @keyup.enter="applyFilters"
      />
      <button type="button" class="secondary" @click="applyFilters">查询</button>
    </section>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>标题</th>
            <th>难度</th>
            <th>分类</th>
            <th>标签</th>
            <th>状态</th>
            <th>创建时间</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="questions.length === 0">
            <td colspan="7" class="empty-cell">暂无题目</td>
          </tr>
          <template v-else>
            <tr v-for="q in questions" :key="q.id">
              <td>{{ q.title }}</td>
              <td>{{ q.difficulty || '-' }}</td>
              <td>{{ categoryName(q.categoryId) }}</td>
              <td>{{ (q.tags || []).join('、') || '-' }}</td>
              <td>
                <span class="status-badge" :class="q.published ? 'published' : 'draft'">
                  {{ q.published ? '已发布' : '草稿' }}
                </span>
              </td>
              <td>{{ formatDate(q.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="openEdit(q)">编辑</button>
                  <button type="button" @click="togglePublish(q)">
                    {{ q.published ? '下架' : '发布' }}
                  </button>
                  <button type="button" class="danger" @click="openDelete(q)">删除</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <div class="pagination">
      <button type="button" :disabled="page <= 0 || loading" @click="previousPage">上一页</button>
      <span class="page-input">
        第
        <input v-model="pageInput" type="number" min="1" :max="pageCount" @keyup.enter="goToPage" />
        / {{ pageCount }} 页
      </span>
      <button type="button" :disabled="page >= pageCount - 1 || loading" @click="nextPage">下一页</button>
      <span class="total">共 {{ total }} 条</span>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ editing ? '编辑题目' : '新增题目' }}</h2>
          <button type="button" class="close-button" @click="closeForm">关闭</button>
        </header>
        <form @submit.prevent="submitForm">
          <label class="field">
            <span>题目标题</span>
            <input v-model.trim="form.title" type="text" maxlength="100" />
          </label>
          <label class="field">
            <span>题目描述</span>
            <textarea v-model="form.description" rows="3"></textarea>
          </label>
          <div class="ai-row">
            <button type="button" class="ai-btn" :disabled="aiGenerating || !form.description" @click="aiGenerate">
              {{ aiGenerating ? 'AI 生成中...' : '✨ AI 生成测试用例' }}
            </button>
            <span class="ai-hint">根据标题和描述自动生成方法签名、难度、标签、20 个测试用例</span>
          </div>
          <div class="field-row">
            <label class="field">
              <span>方法名（如 sum）</span>
              <input v-model.trim="form.methodName" type="text" maxlength="50" />
            </label>
            <label class="field">
              <span>方法签名（如 int[] twoSum(int[], int)）</span>
              <input v-model.trim="form.methodSignature" type="text" maxlength="200" />
            </label>
          </div>
          <div class="field-row">
            <label class="field">
              <span>编程语言</span>
              <input v-model.trim="form.language" type="text" maxlength="20" />
            </label>
            <label class="field">
              <span>难度</span>
              <select v-model="form.difficulty">
                <option v-for="d in difficultyOptions" :key="d" :value="d">{{ d }}</option>
              </select>
            </label>
          </div>
          <label class="field">
            <span>分类</span>
            <select v-model="form.categoryId">
              <option value="">未分类</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </label>
          <label class="field">
            <span>标签（用逗号分隔）</span>
            <input v-model.trim="form.tagsStr" type="text" placeholder="数学、基础" />
          </label>

          <!-- 测试用例编辑 -->
          <div class="field">
            <span>测试用例</span>
            <div v-for="(tc, index) in form.testCases" :key="index" class="testcase-row">
              <input v-model.trim="tc.name" type="text" placeholder="用例名" />
              <input v-model.trim="tc.input" type="text" placeholder="输入" />
              <input v-model.trim="tc.expected" type="text" placeholder="期望输出" />
              <button type="button" class="danger" @click="removeTestCase(index)">删除</button>
            </div>
            <button type="button" class="secondary" @click="addTestCase">添加测试用例</button>
          </div>

          <label class="checkbox-field">
            <input v-model="form.published" type="checkbox" />
            <span>立即发布（发布后学生可见）</span>
          </label>

          <p v-if="formError" class="form-error">{{ formError }}</p>
          <footer class="modal-footer">
            <button type="button" class="secondary" @click="closeForm">取消</button>
            <button type="submit" class="primary" :disabled="submitting">
              {{ submitting ? '保存中...' : '保存' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="confirmOpen" class="modal-backdrop" @click.self="closeConfirm">
      <section class="modal compact" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>删除题目</h2>
          <button type="button" class="close-button" @click="closeConfirm">关闭</button>
        </header>
        <div class="confirm-body">
          <p>确认删除题目 <strong>{{ action?.title }}</strong> 吗？</p>
          <p v-if="confirmError" class="form-error">{{ confirmError }}</p>
        </div>
        <footer class="modal-footer">
          <button type="button" class="secondary" @click="closeConfirm">取消</button>
          <button type="button" class="danger" :disabled="confirming" @click="confirmDelete">
            {{ confirming ? '处理中...' : '确认删除' }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  aiGenerateQuestion,
  createQuestion,
  deleteQuestion,
  getQuestion,
  listCategories,
  listQuestions,
  publishQuestion,
  updateQuestion
} from '../../api/teacher'

const difficultyOptions = ['简单', '中等', '困难']

const questions = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const pageInput = ref('1')
const keyword = ref('')
const difficulty = ref('')
const categoryId = ref('')
const tag = ref('')
const loading = ref(false)
const error = ref('')

const categories = ref([])

const formOpen = ref(false)
const editing = ref(null)
const form = reactive({
  title: '',
  description: '',
  methodName: '',
  methodSignature: '',
  language: 'Java',
  difficulty: '简单',
  categoryId: '',
  tagsStr: '',
  testCases: [],
  published: false
})
const formError = ref('')
const aiGenerating = ref(false)

function extractJson(text) {
  let s = text.replace(/```json\s*/g, '').replace(/```\s*/g, '').trim()
  try { return JSON.parse(s) } catch {}
  const start = s.indexOf('{')
  const end = s.lastIndexOf('}')
  if (start >= 0 && end > start) {
    try { return JSON.parse(s.substring(start, end + 1)) } catch {}
  }
  return null
}

async function aiGenerate() {
  if (!form.description) return
  aiGenerating.value = true
  try {
    const res = await aiGenerateQuestion({ title: form.title, description: form.description })
    const data = extractJson(res.data)
    if (!data) throw new Error('AI 返回的内容无法解析为 JSON')
    if (data.methodSignature) form.methodSignature = data.methodSignature
    if (data.methodName) form.methodName = data.methodName
    if (data.difficulty) form.difficulty = data.difficulty
    if (data.tags) form.tagsStr = data.tags.join(', ')
    if (data.testCases && data.testCases.length) {
      form.testCases = data.testCases.map(tc => ({
        name: tc.name || '',
        input: tc.input || '',
        expected: tc.expected || ''
      }))
    }
  } catch (e) {
    alert('AI 生成失败：' + (e.message || '请检查 AI 配置'))
  } finally {
    aiGenerating.value = false
  }
}
const submitting = ref(false)

const confirmOpen = ref(false)
const action = ref(null)
const confirmError = ref('')
const confirming = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function categoryName(id) {
  if (!id) return '-'
  const found = categories.value.find((c) => c.id === id)
  return found ? found.name : '-'
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function loadCategories() {
  try {
    const res = await listCategories()
    categories.value = res.data || []
  } catch (e) {
    // 分类加载失败不阻塞主流程
  }
}

async function loadQuestions() {
  loading.value = true
  error.value = ''
  try {
    const res = await listQuestions({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      difficulty: difficulty.value,
      categoryId: categoryId.value,
      tag: tag.value
    })
    questions.value = res.data.list || []
    total.value = res.data.total || 0
    pageInput.value = String(page.value + 1)
  } catch (e) {
    error.value = e.message || '题目列表加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  pageInput.value = '1'
  loadQuestions()
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
    loadQuestions()
  }
}

function nextPage() {
  if (page.value < pageCount.value - 1) {
    page.value += 1
    loadQuestions()
  }
}

function goToPage() {
  const target = Number(pageInput.value)
  if (!Number.isInteger(target) || target < 1 || target > pageCount.value) {
    pageInput.value = String(page.value + 1)
    return
  }
  page.value = target - 1
  loadQuestions()
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.methodName = ''
  form.methodSignature = ''
  form.language = 'Java'
  form.difficulty = '简单'
  form.categoryId = ''
  form.tagsStr = ''
  form.testCases = []
  form.published = false
  formError.value = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  formOpen.value = true
}

async function openEdit(q) {
  editing.value = q
  formError.value = ''
  // 详情接口带描述和测试用例，用于回显完整内容
  try {
    const res = await getQuestion(q.id)
    const d = res.data
    form.title = d.title || ''
    form.description = d.description || ''
    form.methodName = d.methodName || ''
    form.methodSignature = d.methodSignature || ''
    form.language = d.language || 'Java'
    form.difficulty = d.difficulty || '简单'
    form.categoryId = d.categoryId || ''
    form.tagsStr = (d.tags || []).join(',')
    form.testCases = (d.testCases || []).map((tc) => ({ ...tc }))
    form.published = !!d.published
    formOpen.value = true
  } catch (e) {
    error.value = e.message || '题目详情加载失败'
  }
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

function addTestCase() {
  form.testCases.push({ name: '', input: '', expected: '' })
}

function removeTestCase(index) {
  form.testCases.splice(index, 1)
}

function validateForm() {
  if (!form.title) {
    formError.value = '题目标题不能为空'
    return false
  }
  if (!form.description) {
    formError.value = '题目描述不能为空'
    return false
  }
  if (!form.methodName) {
    formError.value = '方法名不能为空'
    return false
  }
  return true
}

async function submitForm() {
  if (!validateForm()) return
  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      title: form.title,
      description: form.description,
      methodName: form.methodName,
      methodSignature: form.methodSignature,
      language: form.language,
      difficulty: form.difficulty,
      categoryId: form.categoryId || null,
      tags: form.tagsStr
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean),
      testCases: form.testCases,
      published: form.published
    }
    if (editing.value) {
      await updateQuestion(editing.value.id, payload)
    } else {
      await createQuestion(payload)
    }
    closeForm()
    await loadQuestions()
  } catch (e) {
    formError.value = e.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

async function togglePublish(q) {
  try {
    await publishQuestion(q.id, !q.published)
    await loadQuestions()
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function openDelete(q) {
  action.value = q
  confirmError.value = ''
  confirmOpen.value = true
}

function closeConfirm() {
  confirmOpen.value = false
  action.value = null
}

async function confirmDelete() {
  if (!action.value) return
  confirming.value = true
  confirmError.value = ''
  try {
    await deleteQuestion(action.value.id)
    closeConfirm()
    await loadQuestions()
  } catch (e) {
    confirmError.value = e.message || '删除失败'
  } finally {
    confirming.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadQuestions()
})
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
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

.header-actions,
.row-actions,
.modal-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

button,
.toolbar input,
.toolbar select,
.field input,
.field select,
.field textarea,
.page-input input {
  min-height: 36px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  font: inherit;
}

button {
  padding: 0 12px;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.primary {
  border-color: #059669;
  background: #059669;
  color: #fff;
}

.secondary {
  background: #fff;
  color: #059669;
  border-color: #a7f3d0;
}

.danger {
  border-color: #fca5a5;
  background: #fff;
  color: #b91c1c;
}

.close-button {
  border: 0;
  background: transparent;
  color: #6b7280;
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

.toolbar input[type='text'],
.toolbar select {
  padding: 0 10px;
}

.toolbar input[type='text'] {
  min-width: 180px;
}

.error-banner,
.form-error {
  color: #b91c1c;
  font-size: 14px;
  margin: 0 0 12px;
}

.table-shell {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

table {
  width: 100%;
  min-width: 860px;
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
  width: 220px;
}

.row-actions {
  flex-wrap: wrap;
}

.row-actions button {
  padding: 0 9px;
  min-height: 30px;
  font-size: 13px;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.published {
  background: #d1fae5;
  color: #047857;
}

.status-badge.draft {
  background: #f3f4f6;
  color: #4b5563;
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

.page-input input {
  width: 52px;
  padding: 0 6px;
  text-align: center;
}

.total {
  margin-left: auto;
}

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
  width: min(680px, 100%);
  max-height: 90vh;
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.modal.compact {
  width: min(430px, 100%);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.modal-header h2 {
  font-size: 18px;
}

.field {
  display: block;
  margin-bottom: 14px;
}

.field span {
  display: block;
  margin-bottom: 6px;
  color: #4b5563;
  font-size: 13px;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 0 10px;
}

.field textarea {
  padding: 8px 10px;
  min-height: 60px;
  resize: vertical;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 600px) {
  .field-row {
    grid-template-columns: 1fr;
  }
}

.testcase-row {
  display: grid;
  grid-template-columns: 1.2fr 1.5fr 1.5fr auto;
  gap: 8px;
  margin-bottom: 8px;
}

.testcase-row input {
  width: 100%;
  padding: 0 8px;
  min-height: 32px;
}

.checkbox-field {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: #4b5563;
  font-size: 14px;
}

.modal-footer {
  justify-content: flex-end;
  margin-top: 20px;
}

.confirm-body {
  color: #4b5563;
  line-height: 1.6;
}

.ai-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.ai-btn {
  padding: 8px 16px;
  border: 1px solid #059669;
  border-radius: 6px;
  background: #059669;
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
}

.ai-btn:hover {
  background: #047857;
}

.ai-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-hint {
  color: #6b7280;
  font-size: 13px;
}
</style>
