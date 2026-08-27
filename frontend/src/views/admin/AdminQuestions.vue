<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>题库管理</h1>
        <p>外部题目导入、JSON 模板导入与测试用例维护</p>
      </div>
      <div class="header-actions">
        <button type="button" class="primary" @click="openCreate">新增题目</button>
      </div>
    </header>

    <nav class="tabs">
      <button type="button" :class="{ active: activeTab === 'library' }" @click="activeTab = 'library'">题库</button>
      <button type="button" :class="{ active: activeTab === 'external' }" @click="activeTab = 'external'">外部 API 导入</button>
    </nav>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section v-if="activeTab === 'library'" class="panel">
      <div class="toolbar">
        <input v-model.trim="filters.keyword" type="text" placeholder="搜索标题" @keyup.enter="applyFilters" />
        <select v-model="filters.difficulty" @change="applyFilters">
          <option value="">全部难度</option>
          <option value="简单">简单</option>
          <option value="中等">中等</option>
          <option value="困难">困难</option>
        </select>
        <select v-model="filters.published" @change="applyFilters">
          <option value="">全部状态</option>
          <option value="true">已发布</option>
          <option value="false">草稿</option>
        </select>
        <button type="button" class="secondary" @click="applyFilters">查询</button>

        <div class="template-import">
          <button type="button" class="secondary" :disabled="templateDownloading" @click="handleTemplateDownload">
            {{ templateDownloading ? '下载中...' : '下载模板' }}
          </button>
          <input ref="templateInput" type="file" accept=".json,application/json" @change="selectTemplate" />
          <button type="button" class="secondary" :disabled="!templateFile || templateImporting" @click="handleTemplateImport">
            {{ templateImporting ? '导入中...' : '导入 JSON' }}
          </button>
        </div>
      </div>

      <div class="table-shell">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th>难度</th>
              <th>语言</th>
              <th>方法名</th>
              <th>状态</th>
              <th>来源</th>
              <th>标签</th>
              <th class="actions-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="empty-cell">加载中...</td>
            </tr>
            <tr v-else-if="questions.length === 0">
              <td colspan="8" class="empty-cell">暂无题目</td>
            </tr>
            <template v-else>
              <tr v-for="question in questions" :key="question.id">
                <td>{{ question.title }}</td>
                <td><span class="badge" :class="difficultyClass(question.difficulty)">{{ question.difficulty }}</span></td>
                <td>{{ question.language }}</td>
                <td>{{ question.methodName || '-' }}</td>
                <td>
                  <span class="badge" :class="question.published ? 'published' : 'draft'">
                    {{ question.published ? '已发布' : '草稿' }}
                  </span>
                </td>
                <td>
                  <a v-if="question.sourceUrl" :href="question.sourceUrl" target="_blank" rel="noreferrer">
                    {{ question.sourcePlatform }}
                  </a>
                  <span v-else>-</span>
                </td>
                <td class="tags-cell">{{ (question.tags || []).join('、') || '-' }}</td>
                <td>
                  <div class="row-actions">
                    <button type="button" @click="openEdit(question)">编辑</button>
                  <button type="button" @click="togglePublish(question)">
                    {{ question.published ? '下架' : '发布' }}
                  </button>
                  <button type="button" class="danger" @click="openDelete(question)">删除</button>
                </div>
              </td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button type="button" :disabled="page <= 0 || loading" @click="changePage(-1)">上一页</button>
        <span>
          第
          <input v-model="pageInput" type="number" min="1" :max="pageCount" @keyup.enter="goToPage" />
          / {{ pageCount }} 页
        </span>
        <button type="button" :disabled="page >= pageCount - 1 || loading" @click="changePage(1)">下一页</button>
        <span class="total">共 {{ total }} 条</span>
      </div>
    </section>

    <section v-else class="panel">
      <div class="toolbar">
        <select v-model="external.platform">
          <option value="LEETCODE_CN">LeetCode 中文站</option>
          <option value="LUOGU">洛谷</option>
        </select>
        <input
          v-model.trim="external.keyword"
          type="text"
          :placeholder="external.platform === 'LEETCODE_CN' ? '请输入 titleSlug，例如 two-sum' : '请输入洛谷题号，例如 P1001'"
          @keyup.enter="searchExternalQuestions"
        />
        <select v-model="external.difficulty">
          <option value="">全部难度</option>
          <option value="简单">简单</option>
          <option value="中等">中等</option>
          <option value="困难">困难</option>
        </select>
        <button type="button" class="primary" :disabled="externalLoading" @click="searchExternalQuestions">
          {{ externalLoading ? '搜索中...' : '搜索' }}
        </button>
      </div>

      <div class="table-shell">
        <table>
          <thead>
            <tr>
              <th>平台</th>
              <th>题号</th>
              <th>标题</th>
              <th>难度</th>
              <th>标签</th>
              <th class="actions-column">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="externalResults.length === 0">
              <td colspan="6" class="empty-cell">
                {{ external.platform === 'LEETCODE_CN' ? '请先输入 titleSlug 搜索' : '请输入洛谷题号搜索' }}
              </td>
            </tr>
            <template v-else>
              <tr v-for="item in externalResults" :key="`${item.sourcePlatform}-${item.sourceId}`">
                <td>{{ item.sourcePlatform }}</td>
                <td>{{ item.sourceId }}</td>
                <td>
                  <a :href="item.sourceUrl" target="_blank" rel="noreferrer">{{ item.title }}</a>
                </td>
                <td>{{ item.difficulty }}</td>
                <td class="tags-cell">{{ (item.tags || []).join('、') || '-' }}</td>
                <td>
                  <button type="button" class="primary" :disabled="externalImporting" @click="importExternalCandidate(item)">
                    导入
                  </button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
      <section class="modal question-modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ editingId ? '编辑题目' : '新增题目' }}</h2>
          <button type="button" class="close-button" @click="closeForm">关闭</button>
        </header>
        <form @submit.prevent="submitForm">
          <label class="field">
            <span>标题</span>
            <input v-model.trim="form.title" type="text" maxlength="200" />
          </label>
          <label class="field">
            <span>题目描述</span>
            <textarea v-model="form.description" rows="4" maxlength="10000"></textarea>
          </label>
          <div class="form-grid">
            <label class="field">
              <span>方法名</span>
              <input v-model.trim="form.methodName" type="text" maxlength="100" />
            </label>
            <label class="field">
              <span>方法签名（可留空，留空按输入推断全 int）</span>
              <input v-model.trim="form.methodSignature" type="text" maxlength="200" placeholder="如：int sum(int, int)" />
            </label>
            <label class="field">
              <span>语言</span>
              <select v-model="form.language">
                <option value="Java">Java</option>
              </select>
            </label>
            <label class="field">
              <span>难度</span>
              <select v-model="form.difficulty">
                <option value="简单">简单</option>
                <option value="中等">中等</option>
                <option value="困难">困难</option>
              </select>
            </label>
            <label class="field">
              <span>标签</span>
              <input v-model.trim="form.tagsText" type="text" placeholder="多个标签用逗号分隔" />
            </label>
          </div>
          <label class="field checkbox">
            <input v-model="form.published" type="checkbox" />
            <span>保存后发布</span>
          </label>

          <div class="testcase-header">
            <h3>测试用例</h3>
            <button type="button" class="secondary" @click="addTestCaseRow">添加用例</button>
          </div>
          <div v-if="form.testCases.length === 0" class="empty-case">暂无测试用例</div>
          <div v-for="(testCase, index) in form.testCases" :key="index" class="testcase-row">
            <input v-model.trim="testCase.name" type="text" placeholder="用例名称" />
            <textarea v-model.trim="testCase.input" rows="2" placeholder="输入"></textarea>
            <textarea v-model.trim="testCase.expected" rows="2" placeholder="期望输出"></textarea>
            <button type="button" class="danger" @click="removeTestCaseRow(index)">删除</button>
          </div>

          <p v-if="formError" class="form-error">{{ formError }}</p>
          <footer class="modal-footer">
            <button type="button" class="secondary" @click="closeForm">取消</button>
            <button type="submit" class="primary" :disabled="saving">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="importResult" class="modal-backdrop" @click.self="closeImportResult">
      <section class="modal import-result" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>导入结果</h2>
          <button type="button" class="close-button" @click="closeImportResult">关闭</button>
        </header>
        <div class="import-summary">
          <span>总数 {{ importResult.total }}</span>
          <span>成功 {{ importResult.successCount }}</span>
          <span>失败 {{ importResult.failedCount }}</span>
        </div>
        <p v-if="importResult.errors.length === 0" class="success-text">全部导入成功</p>
        <div v-else class="import-errors">
          <div v-for="item in importResult.errors" :key="`${item.row}-${item.title}`" class="import-error">
            <strong>第 {{ item.row }} 题</strong>
            <span>{{ item.title || '无标题' }}</span>
            <span>{{ item.reason }}</span>
          </div>
        </div>
        <footer class="modal-footer">
          <button type="button" class="primary" @click="closeImportResult">确定</button>
        </footer>
      </section>
    </div>

    <div v-if="deleteTarget" class="modal-backdrop" @click.self="closeDelete">
      <section class="modal compact" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>删除题目</h2>
          <button type="button" class="close-button" @click="closeDelete">关闭</button>
        </header>
        <p>
          确认删除题目 <strong>{{ deleteTarget.title }}</strong> 吗？此操作不可恢复。
        </p>
        <p v-if="deleteError" class="form-error">{{ deleteError }}</p>
        <footer class="modal-footer">
          <button type="button" class="secondary" @click="closeDelete">取消</button>
          <button type="button" class="danger" :disabled="deleting" @click="confirmDeleteQuestion">
            {{ deleting ? '删除中...' : '确认删除' }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  addTestCase,
  createQuestion,
  deleteQuestion,
  deleteTestCase,
  downloadQuestionTemplate,
  getQuestion,
  importExternal,
  importQuestionTemplate,
  listQuestions,
  publishQuestion,
  searchExternal,
  updateQuestion,
  updateTestCase
} from '../../api/questions'

const activeTab = ref('library')
const loading = ref(false)
const error = ref('')
const questions = ref([])
const total = ref(0)
const page = ref(0)
const size = 10
const pageInput = ref('1')
const filters = reactive({ keyword: '', difficulty: '', published: '' })

const formOpen = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  description: '',
  methodName: '',
  language: 'Java',
  difficulty: '简单',
  tagsText: '',
  published: false,
  testCases: []
})

const external = reactive({ platform: 'LEETCODE_CN', keyword: '', difficulty: '' })
const externalResults = ref([])
const externalLoading = ref(false)
const externalImporting = ref(false)

const templateInput = ref(null)
const templateFile = ref(null)
const templateImporting = ref(false)
const templateDownloading = ref(false)
const importResult = ref(null)
const deleteTarget = ref(null)
const deleting = ref(false)
const deleteError = ref('')

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))

function buildListParams() {
  return {
    page: page.value,
    size,
    keyword: filters.keyword,
    difficulty: filters.difficulty,
    published: filters.published
  }
}

async function loadQuestions() {
  loading.value = true
  error.value = ''
  try {
    const response = await listQuestions(buildListParams())
    questions.value = response.data.list || []
    total.value = response.data.total || 0
    pageInput.value = String(page.value + 1)
  } catch (e) {
    error.value = e.message || '题目列表加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  loadQuestions()
}

function changePage(delta) {
  const next = page.value + delta
  if (next < 0 || next >= pageCount.value) return
  page.value = next
  loadQuestions()
}

function goToPage() {
  const target = Number(pageInput.value)
  if (!Number.isInteger(target) || target < 1 || target > pageCount.value) return
  page.value = target - 1
  loadQuestions()
}

function openCreate() {
  editingId.value = null
  formError.value = ''
  Object.assign(form, {
    title: '',
    description: '',
    methodName: '',
    methodSignature: '',
    language: 'Java',
    difficulty: '简单',
    tagsText: '',
    published: false,
    testCases: []
  })
  formOpen.value = true
}

async function openEdit(question) {
  try {
    const response = await getQuestion(question.id)
    const detail = response.data
    editingId.value = detail.id
    formError.value = ''
    Object.assign(form, {
      title: detail.title,
      description: detail.description || '',
      methodName: detail.methodName || '',
      methodSignature: detail.methodSignature || '',
      language: detail.language || 'Java',
      difficulty: detail.difficulty || '简单',
      tagsText: (detail.tags || []).join(','),
      published: detail.published,
      testCases: (detail.testCases || []).map((item) => ({
        name: item.name || '',
        input: item.input || '',
        expected: item.expected || ''
      }))
    })
    formOpen.value = true
  } catch (e) {
    error.value = e.message || '题目详情加载失败'
  }
}

function closeForm() {
  formOpen.value = false
}

function addTestCaseRow() {
  form.testCases.push({ name: '', input: '', expected: '' })
}

function removeTestCaseRow(index) {
  form.testCases.splice(index, 1)
}

async function submitForm() {
  saving.value = true
  formError.value = ''
  const payload = {
    title: form.title,
    description: form.description,
    methodName: form.methodName,
    methodSignature: form.methodSignature || null,
    language: form.language,
    difficulty: form.difficulty,
    tags: form.tagsText.split(',').map((item) => item.trim()).filter(Boolean),
    published: form.published,
    testCases: form.testCases
  }
  try {
    if (editingId.value) {
      await updateQuestion(editingId.value, payload)
    } else {
      await createQuestion(payload)
    }
    formOpen.value = false
    await loadQuestions()
  } catch (e) {
    formError.value = e.message || '题目保存失败'
  } finally {
    saving.value = false
  }
}

async function togglePublish(question) {
  try {
    await publishQuestion(question.id, !question.published)
    await loadQuestions()
  } catch (e) {
    error.value = e.message || '发布状态修改失败'
  }
}

function openDelete(question) {
  deleteTarget.value = question
  deleteError.value = ''
}

function closeDelete() {
  deleteTarget.value = null
  deleteError.value = ''
}

async function confirmDeleteQuestion() {
  if (!deleteTarget.value) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteQuestion(deleteTarget.value.id)
    closeDelete()
    await loadQuestions()
  } catch (e) {
    deleteError.value = e.message || '题目删除失败'
  } finally {
    deleting.value = false
  }
}

async function searchExternalQuestions() {
  externalLoading.value = true
  error.value = ''
  externalResults.value = []
  try {
    const response = await searchExternal({
      platform: external.platform,
      keyword: external.keyword,
      difficulty: external.difficulty,
      page: 0,
      size: 50
    })
    externalResults.value = response.data.list || []
  } catch (e) {
    error.value = e.message || '外部题目搜索失败'
  } finally {
    externalLoading.value = false
  }
}

async function importExternalCandidate(candidate) {
  externalImporting.value = true
  error.value = ''
  try {
    await importExternal({
      platform: candidate.sourcePlatform,
      sourceId: candidate.sourceId
    })
    activeTab.value = 'library'
    page.value = 0
    await loadQuestions()
  } catch (e) {
    error.value = e.message || '外部题目导入失败'
  } finally {
    externalImporting.value = false
  }
}

function selectTemplate(event) {
  templateFile.value = event.target.files[0] || null
}

async function handleTemplateImport() {
  if (!templateFile.value) return
  templateImporting.value = true
  error.value = ''
  try {
    const response = await importQuestionTemplate(templateFile.value)
    importResult.value = response.data
    templateInput.value.value = ''
    templateFile.value = null
    await loadQuestions()
  } catch (e) {
    error.value = e.message || '模板导入失败'
  } finally {
    templateImporting.value = false
  }
}

async function handleTemplateDownload() {
  templateDownloading.value = true
  try {
    await downloadQuestionTemplate()
  } catch (e) {
    error.value = e.message || '模板下载失败'
  } finally {
    templateDownloading.value = false
  }
}

function closeImportResult() {
  importResult.value = null
}

function difficultyClass(value) {
  if (value === '困难') return 'hard'
  if (value === '中等') return 'medium'
  return 'easy'
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-header h1 { font-size: 24px; margin-bottom: 6px; }
.page-header p { color: #6b7280; font-size: 14px; }
.header-actions, .toolbar, .template-import, .row-actions, .pagination, .testcase-header, .modal-footer { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.tabs { display: flex; gap: 8px; margin-bottom: 14px; border-bottom: 1px solid #e5e7eb; }
.tabs button { border: none; background: transparent; padding: 10px 14px; color: #4b5563; cursor: pointer; border-bottom: 2px solid transparent; }
.tabs button.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 600; }
.panel { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; }
.toolbar { margin-bottom: 14px; }
.toolbar input, .toolbar select { min-width: 160px; }
input, textarea, select, button { font: inherit; }
input, textarea, select { min-height: 36px; padding: 7px 10px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #1f2937; }
textarea { resize: vertical; }
button { min-height: 36px; padding: 0 12px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; color: #1f2937; cursor: pointer; }
button:disabled { opacity: 0.5; cursor: not-allowed; }
button.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
button.secondary { color: #374151; }
button.danger { border-color: #dc2626; color: #dc2626; }
.error-banner, .form-error { color: #b91c1c; margin-bottom: 12px; }
.table-shell { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; min-width: 860px; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: middle; }
th { background: #f9fafb; color: #374151; font-weight: 600; white-space: nowrap; }
.empty-cell { text-align: center; color: #6b7280; padding: 30px 0; }
.actions-column { width: 150px; }
.badge { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 12px; }
.badge.easy, .badge.published { background: #dcfce7; color: #15803d; }
.badge.medium, .badge.draft { background: #fef3c7; color: #b45309; }
.badge.hard { background: #fee2e2; color: #b91c1c; }
.tags-cell { max-width: 220px; }
.pagination { justify-content: center; margin-top: 14px; color: #4b5563; }
.pagination input { width: 60px; min-height: 32px; }
.total { color: #6b7280; }
.modal-backdrop { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center; padding: 20px; z-index: 20; }
.modal { background: #fff; border-radius: 8px; padding: 20px; width: min(900px, 100%); max-height: 90vh; overflow-y: auto; }
.question-modal { width: min(960px, 100%); }
.modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.modal-header h2 { font-size: 18px; }
.close-button { border: none; background: transparent; font-size: 16px; }
.field { display: block; margin-bottom: 12px; color: #374151; }
.field span { display: block; margin-bottom: 6px; font-weight: 600; }
.field input, .field textarea, .field select { width: 100%; box-sizing: border-box; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.checkbox { display: flex; align-items: center; gap: 8px; }
.checkbox span { margin: 0; }
.testcase-header { justify-content: space-between; margin-top: 14px; }
.testcase-header h3 { font-size: 15px; margin: 0; }
.empty-case { border: 1px dashed #d1d5db; border-radius: 6px; padding: 20px; text-align: center; color: #6b7280; }
.testcase-row { display: grid; grid-template-columns: 1fr 1.5fr 1.5fr auto; gap: 8px; align-items: start; margin-bottom: 10px; }
.testcase-row textarea { min-height: 60px; }
.import-summary { display: flex; gap: 12px; margin-bottom: 12px; }
.import-summary span { padding: 5px 10px; background: #f3f4f6; border-radius: 6px; }
.success-text { color: #15803d; }
.import-errors { max-height: 260px; overflow-y: auto; }
.import-error { display: grid; grid-template-columns: 90px 120px 1fr; gap: 10px; padding: 8px 0; border-bottom: 1px solid #e5e7eb; color: #b91c1c; }
@media (max-width: 720px) {
  .form-grid, .testcase-row { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; }
  .header-actions { width: 100%; }
}
</style>
