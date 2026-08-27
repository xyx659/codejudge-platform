<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>用户管理</h1>
        <p>管理学生、教师与管理员账号</p>
      </div>
      <div class="header-actions">
        <button type="button" class="secondary" :disabled="templateLoading" @click="handleTemplateDownload">
          {{ templateLoading ? '下载中...' : '下载模板' }}
        </button>
        <button type="button" class="primary" @click="openCreate">新增用户</button>
      </div>
    </header>

    <section class="toolbar">
      <select v-model="roleFilter" @change="applyFilters">
        <option value="">全部角色</option>
        <option v-for="role in roleOptions" :key="role" :value="role">
          {{ roleText[role] }}
        </option>
      </select>
      <input
        v-model.trim="keyword"
        type="text"
        placeholder="搜索账号或姓名"
        @keyup.enter="applyFilters"
      />
      <button type="button" class="secondary" @click="applyFilters">查询</button>

      <div class="import-control">
        <input
          ref="fileInput"
          type="file"
          accept=".csv,text/csv"
          @change="selectImportFile"
        />
        <button type="button" class="secondary" :disabled="!importFile || importing" @click="handleImport">
          {{ importing ? '导入中...' : '导入 CSV' }}
        </button>
      </div>
    </section>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>账号</th>
            <th>姓名</th>
            <th>角色</th>
            <th>学号</th>
            <th>班级</th>
            <th>创建时间</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="users.length === 0">
            <td colspan="7" class="empty-cell">暂无用户</td>
          </tr>
          <template v-else>
            <tr v-for="user in users" :key="`${user.role}-${user.id}`">
              <td>{{ user.username }}</td>
              <td>{{ user.name }}</td>
              <td>
                <span class="role-badge" :class="roleClass(user.role)">
                  {{ roleText[user.role] || user.role }}
                </span>
              </td>
              <td>{{ user.studentNo || '-' }}</td>
              <td>{{ user.className || '-' }}</td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="openEdit(user)">编辑</button>
                  <button
                    type="button"
                    :disabled="cannotChangeRole(user)"
                    :title="roleDisabledTitle(user)"
                    @click="openRoleDialog(user)"
                  >
                    修改角色
                  </button>
                  <button
                    type="button"
                    class="danger"
                    :disabled="cannotDelete(user)"
                    :title="deleteDisabledTitle(user)"
                    @click="openDelete(user)"
                  >
                    删除
                  </button>
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

    <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ editingUser ? '编辑用户' : '新增用户' }}</h2>
          <button type="button" class="close-button" @click="closeForm">关闭</button>
        </header>
        <form @submit.prevent="submitForm">
          <label class="field">
            <span>角色</span>
            <select v-model="form.role" :disabled="!!editingUser">
              <option v-for="role in roleOptions" :key="role" :value="role">
                {{ roleText[role] }}
              </option>
            </select>
          </label>
          <label v-if="form.role !== 'STUDENT'" class="field">
            <span>工号</span>
            <input v-model.trim="form.username" type="text" maxlength="50" />
          </label>
          <label v-if="form.role === 'STUDENT'" class="field">
            <span>学号（登录账号）</span>
            <input v-model.trim="form.studentNo" type="text" maxlength="20" />
          </label>
          <label class="field">
            <span>姓名</span>
            <input v-model.trim="form.name" type="text" maxlength="50" />
          </label>
          <label class="field">
            <span>{{ editingUser ? '新密码' : '密码' }}</span>
            <input
              v-model="form.password"
              type="password"
              maxlength="100"
              :placeholder="editingUser ? '留空表示不修改' : '请输入密码'"
            />
          </label>
          <label v-if="form.role === 'STUDENT'" class="field">
            <span>班级</span>
            <input v-model.trim="form.className" type="text" maxlength="50" placeholder="如 软件2502" />
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

    <div v-if="confirmOpen" class="modal-backdrop" @click.self="closeConfirm">
      <section class="modal compact" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ confirmType === 'role' ? '修改角色' : '删除用户' }}</h2>
          <button type="button" class="close-button" @click="closeConfirm">关闭</button>
        </header>
        <div class="confirm-body">
          <template v-if="confirmType === 'role'">
            <p>
              将 <strong>{{ actionUser?.username }}</strong>
              从 {{ roleText[actionUser?.role] }} 修改为：
            </p>
            <select v-model="targetRole" class="full-width">
              <option
                v-for="role in roleOptions.filter((item) => item !== actionUser?.role)"
                :key="role"
                :value="role"
              >
                {{ roleText[role] }}
              </option>
            </select>
          </template>
          <p v-else>
            确认删除用户 <strong>{{ actionUser?.username }}</strong> 吗？
          </p>
          <p v-if="confirmError" class="form-error">{{ confirmError }}</p>
        </div>
        <footer class="modal-footer">
          <button type="button" class="secondary" @click="closeConfirm">取消</button>
          <button type="button" class="danger" :disabled="confirming" @click="confirmAction">
            {{ confirming ? '处理中...' : '确认' }}
          </button>
        </footer>
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
          <div v-for="item in importResult.errors" :key="`${item.row}-${item.username}`" class="import-error">
            <strong>第 {{ item.row }} 行</strong>
            <span>{{ item.username || '无用户名' }}</span>
            <span>{{ item.reason }}</span>
          </div>
        </div>
        <footer class="modal-footer">
          <button type="button" class="primary" @click="closeImportResult">确定</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  changeUserRole,
  createUser,
  deleteUser,
  downloadTemplate,
  importUsers,
  listUsers,
  updateUser
} from '../../api/admin'
import { getUser } from '../../utils/auth'

const roleOptions = ['STUDENT', 'TEACHER', 'ADMIN']
const roleText = {
  STUDENT: '学生',
  TEACHER: '教师',
  ADMIN: '管理员'
}
const currentUser = getUser()

const users = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const pageInput = ref('1')
const roleFilter = ref('')
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const formOpen = ref(false)
const editingUser = ref(null)
const form = reactive({
  role: 'STUDENT',
  username: '',
  name: '',
  password: '',
  studentNo: '',
  className: ''
})
const formError = ref('')
const submitting = ref(false)

const confirmOpen = ref(false)
const confirmType = ref('')
const actionUser = ref(null)
const targetRole = ref('TEACHER')
const confirmError = ref('')
const confirming = ref(false)

const fileInput = ref(null)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const templateLoading = ref(false)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const adminCount = computed(() => users.value.filter((user) => user.role === 'ADMIN').length)

function isCurrentAdmin(user) {
  return user.role === 'ADMIN'
    && currentUser?.role === 'ADMIN'
    && currentUser.username === user.username
}

function isLastAdmin(user) {
  return user.role === 'ADMIN' && adminCount.value === 1
}

function cannotChangeRole(user) {
  return isCurrentAdmin(user) || isLastAdmin(user)
}

function cannotDelete(user) {
  return isCurrentAdmin(user) || isLastAdmin(user)
}

function roleDisabledTitle(user) {
  if (isCurrentAdmin(user)) return '不能修改当前登录管理员的角色'
  if (isLastAdmin(user)) return '系统至少需要保留一个管理员'
  return ''
}

function deleteDisabledTitle(user) {
  if (isCurrentAdmin(user)) return '不能删除当前登录管理员'
  if (isLastAdmin(user)) return '系统至少需要保留一个管理员'
  return ''
}

function roleClass(role) {
  return {
    'role-student': role === 'STUDENT',
    'role-teacher': role === 'TEACHER',
    'role-admin': role === 'ADMIN'
  }
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const response = await listUsers({
      page: page.value,
      size: size.value,
      role: roleFilter.value,
      keyword: keyword.value
    })
    users.value = response.data.list || []
    total.value = response.data.total || 0
    pageInput.value = String(page.value + 1)
  } catch (e) {
    error.value = e.message || '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 0
  pageInput.value = '1'
  loadUsers()
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
    loadUsers()
  }
}

function nextPage() {
  if (page.value < pageCount.value - 1) {
    page.value += 1
    loadUsers()
  }
}

function goToPage() {
  const target = Number(pageInput.value)
  if (!Number.isInteger(target) || target < 1 || target > pageCount.value) {
    pageInput.value = String(page.value + 1)
    return
  }
  page.value = target - 1
  loadUsers()
}

function resetForm() {
  form.role = 'STUDENT'
  form.username = ''
  form.name = ''
  form.password = ''
  form.studentNo = ''
  form.className = ''
  formError.value = ''
}

function openCreate() {
  editingUser.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(user) {
  editingUser.value = user
  form.role = user.role
  form.username = user.username
  form.name = user.name
  form.password = ''
  form.studentNo = user.studentNo || ''
  form.className = user.className || ''
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editingUser.value = null
  formError.value = ''
}

function validateForm() {
  if (form.role !== 'STUDENT' && !form.username) {
    formError.value = '工号不能为空'
    return false
  }
  if (form.role === 'STUDENT' && !form.studentNo) {
    formError.value = '学号不能为空'
    return false
  }
  if (!form.name) {
    formError.value = '姓名不能为空'
    return false
  }
  if (!editingUser.value && !form.password) {
    formError.value = '密码不能为空'
    return false
  }
  if (form.password && form.password.length < 6) {
    formError.value = '密码长度不能少于 6 位'
    return false
  }
  return true
}

async function submitForm() {
  if (!validateForm()) return

  submitting.value = true
  formError.value = ''
  try {
    const isStudent = form.role === 'STUDENT'
    const payload = {
      username: isStudent ? null : form.username,
      name: form.name,
      password: form.password || null,
      studentNo: isStudent ? form.studentNo : null,
      className: isStudent ? form.className : null
    }

    if (editingUser.value) {
      await updateUser(editingUser.value.role, editingUser.value.id, payload)
    } else {
      await createUser({
        ...payload,
        role: form.role
      })
    }

    closeForm()
    await loadUsers()
  } catch (e) {
    formError.value = e.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

function openRoleDialog(user) {
  actionUser.value = user
  confirmType.value = 'role'
  targetRole.value = roleOptions.find((role) => role !== user.role) || 'STUDENT'
  confirmError.value = ''
  confirmOpen.value = true
}

function openDelete(user) {
  actionUser.value = user
  confirmType.value = 'delete'
  confirmError.value = ''
  confirmOpen.value = true
}

function closeConfirm() {
  confirmOpen.value = false
  actionUser.value = null
  confirmError.value = ''
}

async function confirmAction() {
  if (!actionUser.value) return

  confirming.value = true
  confirmError.value = ''
  try {
    if (confirmType.value === 'role') {
      await changeUserRole(actionUser.value.role, actionUser.value.id, targetRole.value)
    } else {
      await deleteUser(actionUser.value.role, actionUser.value.id)
    }
    closeConfirm()
    await loadUsers()
  } catch (e) {
    confirmError.value = e.message || '操作失败'
  } finally {
    confirming.value = false
  }
}

function selectImportFile(event) {
  importFile.value = event.target.files?.[0] || null
  error.value = ''
}

async function handleImport() {
  if (!importFile.value) {
    error.value = '请先选择 CSV 文件'
    return
  }

  importing.value = true
  error.value = ''
  try {
    const response = await importUsers(importFile.value)
    importResult.value = response.data
    importFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    await loadUsers()
  } catch (e) {
    error.value = e.message || 'CSV 导入失败'
  } finally {
    importing.value = false
  }
}

function closeImportResult() {
  importResult.value = null
}

async function handleTemplateDownload() {
  templateLoading.value = true
  error.value = ''
  try {
    await downloadTemplate()
  } catch (e) {
    error.value = e.message || '模板下载失败'
  } finally {
    templateLoading.value = false
  }
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadUsers)
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
.import-control,
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
.confirm-body select,
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
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.secondary {
  background: #fff;
  color: #2563eb;
  border-color: #bfdbfe;
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
  min-width: 220px;
}

.toolbar input[type='file'] {
  max-width: 190px;
  padding: 7px 0;
  border: 0;
  font-size: 13px;
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
  min-width: 760px;
  border-collapse: collapse;
}

th,
td {
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  vertical-align: middle;
}

th {
  background: #f8fafc;
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
}

tbody tr:hover {
  background: #f9fafb;
}

.actions-column {
  width: 250px;
}

.row-actions {
  flex-wrap: wrap;
}

.row-actions button {
  padding: 0 9px;
  min-height: 30px;
  font-size: 13px;
}

.role-badge {
  display: inline-block;
  min-width: 52px;
  padding: 4px 8px;
  border-radius: 999px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
}

.role-student {
  background: #dbeafe;
  color: #1d4ed8;
}

.role-teacher {
  background: #ccfbf1;
  color: #0f766e;
}

.role-admin {
  background: #fef3c7;
  color: #92400e;
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
  width: min(520px, 100%);
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
.confirm-body select {
  width: 100%;
  padding: 0 10px;
}

.modal-footer {
  justify-content: flex-end;
  margin-top: 20px;
}

.confirm-body {
  color: #4b5563;
  line-height: 1.6;
}

.confirm-body select {
  margin-top: 10px;
}

.full-width {
  width: 100%;
}

.import-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.import-summary span {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  text-align: center;
  background: #f8fafc;
}

.success-text {
  color: #15803d;
  margin-bottom: 12px;
}

.import-errors {
  display: grid;
  gap: 8px;
  max-height: 300px;
  overflow: auto;
}

.import-error {
  display: grid;
  grid-template-columns: 80px 1fr 1fr;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid #fee2e2;
  border-radius: 6px;
  background: #fff7f7;
  font-size: 13px;
}

.import-error strong {
  color: #b91c1c;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions,
  .import-control,
  .modal-footer,
  .pagination {
    width: 100%;
    flex-wrap: wrap;
  }

  .toolbar input[type='text'],
  .toolbar input[type='file'] {
    width: 100%;
    max-width: none;
    min-width: 0;
  }

  .total {
    margin-left: 0;
  }

  .import-error {
    grid-template-columns: 1fr;
  }
}
</style>
