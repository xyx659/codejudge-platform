<!-- 教师端：分类管理（增删改查） -->
<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>分类管理</h1>
        <p>维护题库与考试的题目分类</p>
      </div>
      <button type="button" class="primary" @click="openCreate">新增分类</button>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>

    <section class="table-shell">
      <table>
        <thead>
          <tr>
            <th>分类名称</th>
            <th>描述</th>
            <th>排序号</th>
            <th class="actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="4" class="empty-cell">加载中...</td>
          </tr>
          <tr v-else-if="categories.length === 0">
            <td colspan="4" class="empty-cell">暂无分类</td>
          </tr>
          <template v-else>
            <tr v-for="category in categories" :key="category.id">
              <td>{{ category.name }}</td>
              <td>{{ category.description || '-' }}</td>
              <td>{{ category.sortOrder }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="openEdit(category)">编辑</button>
                  <button type="button" class="danger" @click="openDelete(category)">删除</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
      <section class="modal" role="dialog" aria-modal="true">
        <header class="modal-header">
          <h2>{{ editing ? '编辑分类' : '新增分类' }}</h2>
          <button type="button" class="close-button" @click="closeForm">关闭</button>
        </header>
        <form @submit.prevent="submitForm">
          <label class="field">
            <span>分类名称</span>
            <input v-model.trim="form.name" type="text" maxlength="50" />
          </label>
          <label class="field">
            <span>描述</span>
            <input v-model.trim="form.description" type="text" maxlength="200" />
          </label>
          <label class="field">
            <span>排序号（数字越小越靠前）</span>
            <input v-model.number="form.sortOrder" type="number" />
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
          <h2>删除分类</h2>
          <button type="button" class="close-button" @click="closeConfirm">关闭</button>
        </header>
        <div class="confirm-body">
          <p>确认删除分类 <strong>{{ action?.name }}</strong> 吗？</p>
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
import { onMounted, reactive, ref } from 'vue'
import { createCategory, deleteCategory, listCategories, updateCategory } from '../../api/teacher'

const categories = ref([])
const loading = ref(false)
const error = ref('')

const formOpen = ref(false)
const editing = ref(null)
const form = reactive({ name: '', description: '', sortOrder: 0 })
const formError = ref('')
const submitting = ref(false)

const confirmOpen = ref(false)
const action = ref(null)
const confirmError = ref('')
const confirming = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listCategories()
    categories.value = res.data || []
  } catch (e) {
    error.value = e.message || '分类列表加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.sortOrder = 0
  formError.value = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(category) {
  editing.value = category
  form.name = category.name
  form.description = category.description || ''
  form.sortOrder = category.sortOrder || 0
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

async function submitForm() {
  if (!form.name) {
    formError.value = '分类名称不能为空'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      name: form.name,
      description: form.description,
      sortOrder: form.sortOrder
    }
    if (editing.value) {
      await updateCategory(editing.value.id, payload)
    } else {
      await createCategory(payload)
    }
    closeForm()
    await load()
  } catch (e) {
    formError.value = e.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

function openDelete(category) {
  action.value = category
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
    await deleteCategory(action.value.id)
    closeConfirm()
    await load()
  } catch (e) {
    confirmError.value = e.message || '删除失败'
  } finally {
    confirming.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  max-width: 900px;
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

button,
.field input {
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
  min-width: 560px;
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
  width: 140px;
}

.row-actions,
.modal-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-actions button {
  padding: 0 9px;
  min-height: 30px;
  font-size: 13px;
}

.empty-cell {
  height: 120px;
  text-align: center;
  color: #6b7280;
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
  width: min(440px, 100%);
  max-height: 90vh;
  overflow: auto;
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.modal.compact {
  width: min(400px, 100%);
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

.field input {
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
</style>
