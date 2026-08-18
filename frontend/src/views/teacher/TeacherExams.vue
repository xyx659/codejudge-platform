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

          <!-- 组卷：左栏搜索/筛选/分页选题目，右栏已选列表里赋分 -->
          <div class="field">
            <span>组卷（左栏搜索、筛选并勾选题目，右栏为每题赋分；仅显示已发布题目）</span>
            <div class="question-picker">
              <!-- 左栏：候选题目 -->
              <section class="picker-column">
                <div class="picker-filters">
                  <input
                    v-model.trim="candidateKeyword"
                    type="text"
                    class="picker-search"
                    placeholder="搜索标题 / 描述…"
                    @keyup.enter="searchCandidates"
                  />
                  <div class="picker-filter-row">
                    <select v-model="candidateDifficulty" @change="onCandidateFilterChange">
                      <option value="">全部难度</option>
                      <option>简单</option>
                      <option>中等</option>
                      <option>困难</option>
                    </select>
                    <select v-model="candidateCategoryId" @change="onCandidateFilterChange">
                      <option value="">全部分类</option>
                      <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
                    </select>
                    <button type="button" class="secondary" @click="searchCandidates">搜索</button>
                  </div>
                </div>
                <div class="picker-head">可选题目（共 {{ candidateTotal }} 道）</div>
                <div class="picker-list">
                  <div v-if="candidateLoading && candidates.length === 0" class="picker-empty">题目加载中...</div>
                  <div v-else-if="candidateTotal === 0" class="picker-empty">
                    {{ candidateKeyword || candidateDifficulty || candidateCategoryId ? '无匹配题目' : '暂无已发布题目，请先在题库发布题目' }}
                  </div>
                  <template v-else>
                    <div v-for="q in candidates" :key="q.id" class="picker-row">
                      <label class="picker-label">
                        <input
                          type="checkbox"
                          :checked="isSelected(q.id)"
                          @change="toggle(q)"
                        />
                        <span class="picker-title" :title="q.title">{{ q.title }}</span>
                        <span class="picker-meta">{{ q.difficulty || '-' }}</span>
                      </label>
                    </div>
                  </template>
                </div>
                <div class="picker-pagination">
                  <button
                    type="button"
                    :disabled="candidatePage <= 0 || candidateLoading"
                    @click="candidatePrevPage"
                  >
                    上一页
                  </button>
                  <span>第 {{ candidatePage + 1 }} / {{ candidatePageCount }} 页</span>
                  <button
                    type="button"
                    :disabled="candidatePage >= candidatePageCount - 1 || candidateLoading"
                    @click="candidateNextPage"
                  >
                    下一页
                  </button>
                </div>
              </section>

              <!-- 右栏：已选题目，填写分值 -->
              <section class="picker-column">
                <div class="picker-head picker-total">
                  已选 {{ form.questions.length }} 道 · 总分 {{ totalScore }}
                </div>
                <div class="picker-list">
                  <div v-if="form.questions.length === 0" class="picker-empty">尚未选择题目</div>
                  <template v-else>
                    <div v-for="q in form.questions" :key="q.questionId" class="selected-row">
                      <span class="selected-title" :title="titleOf(q.questionId)">{{ titleOf(q.questionId) }}</span>
                      <input
                        :value="q.score"
                        type="number"
                        min="0"
                        class="score-input"
                        placeholder="分值"
                        @input="(e) => setScore(q.questionId, e.target.value)"
                      />
                      <button type="button" class="remove-btn" @click="removeQuestion(q.questionId)">移除</button>
                    </div>
                  </template>
                </div>
              </section>
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
// 组卷左栏的搜索 / 筛选 / 分页状态（走服务端筛选 + 分页，避免一次拉全部题目）
const candidateKeyword = ref('')
const candidateDifficulty = ref('')
const candidateCategoryId = ref('')
const candidatePage = ref(0)
const candidateSize = ref(10)
const candidateTotal = ref(0)
// 题目 ID -> 标题 缓存，供右栏「已选」列表跨页 / 编辑回显时显示标题
const titleMap = reactive({})

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
const candidatePageCount = computed(() =>
  Math.max(1, Math.ceil(candidateTotal.value / candidateSize.value))
)
const totalScore = computed(() =>
  form.questions.reduce((sum, q) => sum + (Number(q.score) || 0), 0)
)

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

// 加载「已发布」题目作为组卷候选（服务端筛选 + 分页，只拉当前页）
async function loadCandidates() {
  candidateLoading.value = true
  try {
    const res = await listQuestions({
      page: candidatePage.value,
      size: candidateSize.value,
      published: true,
      keyword: candidateKeyword.value,
      difficulty: candidateDifficulty.value,
      categoryId: candidateCategoryId.value
    })
    const list = res.data.list || []
    candidates.value = list
    candidateTotal.value = res.data.total || 0
    // 把本页题目标题写入缓存，右栏「已选」列表跨页也能显示标题
    list.forEach((q) => {
      titleMap[q.id] = q.title
    })
  } catch (e) {
    candidates.value = []
    candidateTotal.value = 0
  } finally {
    candidateLoading.value = false
  }
}

// 点击「搜索」或回车：回到第一页重新查询
function searchCandidates() {
  candidatePage.value = 0
  loadCandidates()
}

// 切换难度 / 分类筛选：回到第一页重新查询
function onCandidateFilterChange() {
  candidatePage.value = 0
  loadCandidates()
}

function candidatePrevPage() {
  if (candidatePage.value > 0) {
    candidatePage.value -= 1
    loadCandidates()
  }
}

function candidateNextPage() {
  if (candidatePage.value < candidatePageCount.value - 1) {
    candidatePage.value += 1
    loadCandidates()
  }
}

function isSelected(qid) {
  return form.questions.some((q) => q.questionId === qid)
}

// 勾选 / 取消勾选一道题（q 为题目对象，便于缓存标题）
function toggle(q) {
  const qid = q.id
  if (isSelected(qid)) {
    form.questions = form.questions.filter((item) => item.questionId !== qid)
  } else {
    titleMap[qid] = q.title
    form.questions.push({ questionId: qid, score: 0 })
  }
}

// 右栏「移除」按钮：从已选列表去掉一道题
function removeQuestion(qid) {
  form.questions = form.questions.filter((item) => item.questionId !== qid)
}

// 根据题目 ID 取标题（右栏已选列表显示用，缓存未命中则回退显示 ID）
function titleOf(qid) {
  return titleMap[qid] || qid
}

function setScore(qid, value) {
  const found = form.questions.find((q) => q.questionId === qid)
  if (found) {
    found.score = Number(value) || 0
  }
}

// 打开弹窗前把左栏搜索 / 筛选 / 分页重置为默认状态
function prepareCandidates() {
  candidateKeyword.value = ''
  candidateDifficulty.value = ''
  candidateCategoryId.value = ''
  candidatePage.value = 0
  loadCandidates()
}

// 编辑已有考试时，为已选题补全标题（这些题可能不在当前候选页里）
async function resolveQuestionTitles(questions) {
  const missing = questions
    .map((q) => q.questionId)
    .filter((id) => !titleMap[id])
  await Promise.all(
    missing.map(async (id) => {
      try {
        const res = await getQuestion(id)
        titleMap[id] = res.data.title || id
      } catch (e) {
        titleMap[id] = id
      }
    })
  )
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
  prepareCandidates()
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
    prepareCandidates()
    await resolveQuestionTitles(form.questions)
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
  width: min(880px, 100%);
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

/* 组卷：左右两栏穿梭框 */
.question-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}

.picker-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.picker-filters {
  display: grid;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.picker-search {
  width: 100%;
  padding: 0 10px;
  min-height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.picker-filter-row {
  display: flex;
  gap: 8px;
}

.picker-filter-row select {
  flex: 1;
  min-width: 0;
  width: auto;
  padding: 0 8px;
  min-height: 34px;
  font-size: 13px;
}

.picker-filter-row .secondary {
  flex: none;
  padding: 0 10px;
  min-height: 34px;
  font-size: 13px;
}

.picker-head {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.picker-total {
  color: #059669;
}

.picker-list {
  display: grid;
  gap: 4px;
  align-content: start;
  max-height: 260px;
  overflow: auto;
  padding: 8px;
}

.picker-row {
  display: flex;
  align-items: center;
}

.picker-label {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.picker-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-meta {
  flex: none;
  font-size: 12px;
  color: #9ca3af;
}

.picker-empty {
  padding: 20px 8px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.picker-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  font-size: 13px;
  color: #4b5563;
}

.picker-pagination button {
  padding: 0 8px;
  min-height: 28px;
  font-size: 13px;
}

/* 右栏：已选题目行 */
.selected-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-picker .score-input {
  width: 70px;
  flex: none;
  padding: 0 8px;
  min-height: 30px;
}

.remove-btn {
  flex: none;
  padding: 0 8px;
  min-height: 30px;
  font-size: 13px;
  color: #b91c1c;
  border-color: #fca5a5;
  background: #fff;
}

@media (max-width: 600px) {
  .question-picker {
    grid-template-columns: 1fr;
  }
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
