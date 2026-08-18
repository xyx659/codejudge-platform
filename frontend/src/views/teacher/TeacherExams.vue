<!-- 教师端：考试管理（组卷、发布、关闭） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>考试管理</h1>
        <p>组卷、发布考试并跟踪状态</p>
      </div>
      <button type="button" class="primary" @click="openCreate">新建考试</button>
    </header>

    <section class="toolbar">
      <select v-model="status" @change="applyFilters">
        <option value="">全部状态</option>
        <option v-for="s in statusOptions" :key="s" :value="s">{{ statusText[s] }}</option>
      </select>
      <select v-model="categoryId" @change="applyFilters">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button type="button" class="secondary" @click="applyFilters">查询</button>
    </section>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>标题</th>
            <th>状态</th>
            <th>目标班级</th>
            <th>开始时间</th>
            <th>题目数</th>
            <th>总分</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="exams.length === 0">
            <td colspan="7" class="empty-cell">暂无考试</td>
          </tr>
          <template v-else>
            <tr v-for="exam in exams" :key="exam.id">
              <td>{{ exam.title }}</td>
              <td>
                <span class="status-badge" :class="statusClass(exam.status)">
                  {{ statusText[exam.status] || exam.status }}
                </span>
              </td>
              <td>{{ exam.targetClass || '-' }}</td>
              <td>{{ formatDate(exam.startTime) }}</td>
              <td>{{ exam.questionCount }}</td>
              <td>{{ exam.totalScore }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="openEdit(exam)">编辑</button>
                  <button
                    v-if="exam.status === 'DRAFT'"
                    type="button"
                    @click="publish(exam)"
                  >
                    发布
                  </button>
                  <button
                    v-if="exam.status === 'PUBLISHED'"
                    type="button"
                    @click="close(exam)"
                  >
                    关闭
                  </button>
                  <button type="button" class="danger" @click="openDelete(exam)">删除</button>
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

    <!-- 新建 / 编辑弹窗 -->
    <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ editing ? '编辑考试' : '新建考试' }}</h2>
          <button type="button" class="close-button" @click="closeForm">关闭</button>
        </header>
        <form @submit.prevent="submitForm">
          <label class="field">
            <span>考试标题</span>
            <input v-model.trim="form.title" type="text" maxlength="100" />
          </label>
          <label class="field">
            <span>考试说明</span>
            <textarea v-model="form.description" rows="2"></textarea>
          </label>
          <div class="field-row">
            <label class="field">
              <span>分类</span>
              <select v-model="form.categoryId">
                <option value="">未分类</option>
                <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
            </label>
            <label class="field">
              <span>目标班级</span>
              <input v-model.trim="form.targetClass" type="text" placeholder="如 软件工程2101班" />
            </label>
          </div>
          <div class="field-row">
            <label class="field">
              <span>开始时间</span>
              <input v-model="form.startTime" type="datetime-local" />
            </label>
            <label class="field">
              <span>结束时间</span>
              <input v-model="form.endTime" type="datetime-local" />
            </label>
          </div>
          <div class="field-row">
            <label class="field">
              <span>考试时长（分钟）</span>
              <input v-model.number="form.durationMinutes" type="number" min="1" />
            </label>
            <label class="field">
              <span>及格分</span>
              <input v-model.number="form.passScore" type="number" min="0" />
            </label>
          </div>

          <!-- 组卷：勾选题目并给每题赋分 -->
          <div class="field">
            <span>组卷（勾选题目并填写分值，只显示已发布题目）</span>
            <div v-if="candidateLoading" class="picker-loading">题目加载中...</div>
            <div v-else-if="candidates.length === 0" class="picker-loading">暂无已发布题目，请先在题库发布题目</div>
            <div v-else class="question-picker">
              <div v-for="q in candidates" :key="q.id" class="picker-row">
                <label class="picker-label">
                  <input
                    type="checkbox"
                    :checked="isSelected(q.id)"
                    @change="toggle(q.id)"
                  />
                  <span class="picker-title">{{ q.title }}</span>
                  <span class="picker-meta">{{ q.difficulty || '-' }}</span>
                </label>
                <input
                  v-if="isSelected(q.id)"
                  :value="scoreOf(q.id)"
                  type="number"
                  min="0"
                  class="score-input"
                  @input="(e) => setScore(q.id, e.target.value)"
                />
              </div>
            </div>
          </div>

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
          <h2>删除考试</h2>
          <button type="button" class="close-button" @click="closeConfirm">关闭</button>
        </header>
        <div class="confirm-body">
          <p>确认删除考试 <strong>{{ action?.title }}</strong> 吗？</p>
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
  closeExam,
  createExam,
  deleteExam,
  getExam,
  listCategories,
  listExams,
  listQuestions,
  publishExam,
  updateExam
} from '../../api/teacher'

const statusOptions = ['DRAFT', 'PUBLISHED', 'CLOSED']
const statusText = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  CLOSED: '已结束'
}

const exams = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const pageInput = ref('1')
const status = ref('')
const categoryId = ref('')
const loading = ref(false)
const error = ref('')

const categories = ref([])
const candidates = ref([])
const candidateLoading = ref(false)

const formOpen = ref(false)
const editing = ref(null)
const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  targetClass: '',
  startTime: '',
  endTime: '',
  durationMinutes: 60,
  passScore: 60,
  questions: []
})
const formError = ref('')
const submitting = ref(false)

const confirmOpen = ref(false)
const action = ref(null)
const confirmError = ref('')
const confirming = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

function statusClass(s) {
  return {
    'status-draft': s === 'DRAFT',
    'status-published': s === 'PUBLISHED',
    'status-closed': s === 'CLOSED'
  }
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
    // 忽略
  }
}

async function loadExams() {
  loading.value = true
  error.value = ''
  try {
    const res = await listExams({
      page: page.value,
      size: size.value,
      status: status.value,
      categoryId: categoryId.value
    })
    exams.value = res.data.list || []
    total.value = res.data.total || 0
    pageInput.value = String(page.value + 1)
  } catch (e) {
    error.value = e.message || '考试列表加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  pageInput.value = '1'
  loadExams()
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
    loadExams()
  }
}

function nextPage() {
  if (page.value < pageCount.value - 1) {
    page.value += 1
    loadExams()
  }
}

function goToPage() {
  const target = Number(pageInput.value)
  if (!Number.isInteger(target) || target < 1 || target > pageCount.value) {
    pageInput.value = String(page.value + 1)
    return
  }
  page.value = target - 1
  loadExams()
}

// 加载「已发布」题目作为组卷候选
async function loadCandidates() {
  candidateLoading.value = true
  try {
    const res = await listQuestions({ size: 1000 })
    candidates.value = (res.data.list || []).filter((q) => q.published)
  } catch (e) {
    candidates.value = []
  } finally {
    candidateLoading.value = false
  }
}

function isSelected(qid) {
  return form.questions.some((q) => q.questionId === qid)
}

function toggle(qid) {
  if (isSelected(qid)) {
    form.questions = form.questions.filter((q) => q.questionId !== qid)
  } else {
    form.questions.push({ questionId: qid, score: 0 })
  }
}

function scoreOf(qid) {
  const found = form.questions.find((q) => q.questionId === qid)
  return found ? found.score : 0
}

function setScore(qid, value) {
  const found = form.questions.find((q) => q.questionId === qid)
  if (found) {
    found.score = Number(value) || 0
  }
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.categoryId = ''
  form.targetClass = ''
  form.startTime = ''
  form.endTime = ''
  form.durationMinutes = 60
  form.passScore = 60
  form.questions = []
  formError.value = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  formOpen.value = true
}

// datetime-local 输入需要 "YYYY-MM-DDTHH:mm" 格式（去掉秒）
function toLocalInput(value) {
  return value ? String(value).slice(0, 16) : ''
}

async function openEdit(exam) {
  editing.value = exam
  formError.value = ''
  try {
    const res = await getExam(exam.id)
    const d = res.data
    form.title = d.title || ''
    form.description = d.description || ''
    form.categoryId = d.categoryId || ''
    form.targetClass = d.targetClass || ''
    form.startTime = toLocalInput(d.startTime)
    form.endTime = toLocalInput(d.endTime)
    form.durationMinutes = d.durationMinutes || 60
    form.passScore = d.passScore || 0
    form.questions = (d.questions || []).map((q) => ({ questionId: q.questionId, score: q.score || 0 }))
    formOpen.value = true
  } catch (e) {
    error.value = e.message || '考试详情加载失败'
  }
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

// 补全秒，避免 Jackson 解析 LocalDateTime 时报错
function normalizeDateTime(value) {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

function validateForm() {
  if (!form.title) {
    formError.value = '考试标题不能为空'
    return false
  }
  if (form.questions.length === 0) {
    formError.value = '请至少选择一道题目'
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
      categoryId: form.categoryId || null,
      targetClass: form.targetClass,
      startTime: normalizeDateTime(form.startTime),
      endTime: normalizeDateTime(form.endTime),
      durationMinutes: Number(form.durationMinutes) || null,
      passScore: Number(form.passScore) || 0,
      questions: form.questions.map((q) => ({
        questionId: q.questionId,
        score: Number(q.score) || 0
      }))
    }
    if (editing.value) {
      await updateExam(editing.value.id, payload)
    } else {
      await createExam(payload)
    }
    closeForm()
    await loadExams()
  } catch (e) {
    formError.value = e.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

async function publish(exam) {
  try {
    await publishExam(exam.id)
    await loadExams()
  } catch (e) {
    error.value = e.message || '发布失败'
  }
}

async function close(exam) {
  try {
    await closeExam(exam.id)
    await loadExams()
  } catch (e) {
    error.value = e.message || '关闭失败'
  }
}

function openDelete(exam) {
  action.value = exam
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
    await deleteExam(action.value.id)
    closeConfirm()
    await loadExams()
  } catch (e) {
    confirmError.value = e.message || '删除失败'
  } finally {
    confirming.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadExams()
  loadCandidates()
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

.row-actions,
.modal-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

button,
.toolbar select,
.field input,
.field select,
.field textarea,
.page-input input,
.score-input {
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

.toolbar select {
  padding: 0 10px;
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
  width: 210px;
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

.status-draft {
  background: #f3f4f6;
  color: #4b5563;
}

.status-published {
  background: #d1fae5;
  color: #047857;
}

.status-closed {
  background: #fee2e2;
  color: #b91c1c;
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
  width: min(720px, 100%);
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
  min-height: 50px;
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

.picker-loading {
  padding: 14px;
  color: #6b7280;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.question-picker {
  display: grid;
  gap: 6px;
  max-height: 260px;
  overflow: auto;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.picker-label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  cursor: pointer;
}

.picker-title {
  font-size: 14px;
  color: #1f2937;
}

.picker-meta {
  font-size: 12px;
  color: #9ca3af;
}

.score-input {
  width: 80px;
  padding: 0 8px;
  min-height: 30px;
}

.modal-footer {
  justify-content: flex-end;
  margin-top: 20px;
}

.confirm-body {
  color: #4b5563;
  line-height: 1.6;
}
</style>
