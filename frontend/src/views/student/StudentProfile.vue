<!-- 学生端：个人信息，查看账号信息，支持修改姓名与登录密码 -->
<template>
  <div class="page">
    <h1>个人信息</h1>
    <p class="desc">查看账号信息，可修改姓名与登录密码</p>

    <p v-if="loading" class="hint">加载中...</p>
    <p v-else-if="error" class="hint error">{{ error }}</p>

    <template v-else-if="profile">
      <!-- 基本信息 -->
      <div class="card">
        <div class="label">基本信息</div>
        <table class="info">
          <tbody>
            <tr><th>账号</th><td>{{ profile.username }}</td></tr>
            <tr><th>姓名</th><td>{{ profile.name }}</td></tr>
            <tr><th>角色</th><td>{{ roleText(profile.role) }}</td></tr>
            <tr><th>学号</th><td>{{ profile.studentNo || '—' }}</td></tr>
            <tr><th>班级</th><td>{{ profile.className || '—' }}</td></tr>
            <tr><th>注册时间</th><td>{{ formatTime(profile.createdAt) }}</td></tr>
          </tbody>
        </table>
      </div>

      <!-- 修改姓名 -->
      <div class="card">
        <div class="label">修改姓名</div>
        <div class="form-row">
          <input v-model.trim="nameInput" class="input" placeholder="请输入新姓名" />
          <button class="btn primary" :disabled="nameSaving" @click="saveName">
            {{ nameSaving ? '保存中...' : '保存姓名' }}
          </button>
        </div>
        <p v-if="nameMsg" class="msg" :class="{ ok: nameOk }">{{ nameMsg }}</p>
      </div>

      <!-- 修改密码 -->
      <div class="card">
        <div class="label">修改密码</div>
        <div class="form-col">
          <input v-model="oldPassword" type="password" class="input" placeholder="原密码" />
          <input v-model="newPassword" type="password" class="input" placeholder="新密码" />
          <input v-model="confirmPassword" type="password" class="input" placeholder="确认新密码" />
          <div>
            <button class="btn primary" :disabled="pwdSaving" @click="savePassword">
              {{ pwdSaving ? '提交中...' : '修改密码' }}
            </button>
          </div>
        </div>
        <p v-if="pwdMsg" class="msg" :class="{ ok: pwdOk }">{{ pwdMsg }}</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { changePassword, getProfile, updateProfile } from '../../api/student'
import { getUser, setUser } from '../../utils/auth'

const profile = ref(null)
const loading = ref(false)
const error = ref('')

// 修改姓名
const nameInput = ref('')
const nameSaving = ref(false)
const nameMsg = ref('')
const nameOk = ref(false)

// 修改密码
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const pwdSaving = ref(false)
const pwdMsg = ref('')
const pwdOk = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getProfile()
    profile.value = res.data
    nameInput.value = res.data.name || ''
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function saveName() {
  const name = nameInput.value
  if (!name) {
    nameMsg.value = '姓名不能为空'
    nameOk.value = false
    return
  }
  nameSaving.value = true
  nameMsg.value = ''
  try {
    const res = await updateProfile({ name })
    profile.value = res.data
    // 同步更新本地缓存的用户姓名
    const user = getUser()
    if (user) setUser({ ...user, name: res.data.name })
    nameMsg.value = '姓名已更新'
    nameOk.value = true
  } catch (e) {
    nameMsg.value = e.message || '保存失败'
    nameOk.value = false
  } finally {
    nameSaving.value = false
  }
}

async function savePassword() {
  if (!oldPassword.value) {
    pwdMsg.value = '请输入原密码'
    pwdOk.value = false
    return
  }
  if (!newPassword.value) {
    pwdMsg.value = '请输入新密码'
    pwdOk.value = false
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    pwdMsg.value = '两次输入的新密码不一致'
    pwdOk.value = false
    return
  }
  pwdSaving.value = true
  pwdMsg.value = ''
  try {
    await changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    pwdMsg.value = '密码已修改'
    pwdOk.value = true
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    pwdMsg.value = e.message || '修改失败'
    pwdOk.value = false
  } finally {
    pwdSaving.value = false
  }
}

function roleText(role) {
  if (role === 'STUDENT') return '学生'
  if (role === 'TEACHER') return '教师'
  if (role === 'ADMIN') return '管理员'
  return role || '—'
}

function formatTime(s) {
  return s ? s.replace('T', ' ') : '—'
}

onMounted(load)
</script>

<style scoped>
.page h1 {
  font-size: 24px;
  margin-bottom: 8px;
}

.page .desc {
  color: #6b7280;
  margin-bottom: 20px;
}

.hint {
  color: #6b7280;
  padding: 20px 0;
}

.hint.error {
  color: #dc2626;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
  margin-bottom: 18px;
}

.label {
  font-weight: 600;
  margin-bottom: 12px;
}

.info {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.info th,
.info td {
  padding: 8px 0;
  text-align: left;
  border-bottom: 1px solid #f3f4f6;
}

.info th {
  width: 120px;
  color: #6b7280;
  font-weight: 600;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 320px;
}

.input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
}

.btn {
  padding: 8px 16px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  cursor: pointer;
  font-size: 14px;
}

.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.msg {
  margin-top: 10px;
  font-size: 14px;
  color: #dc2626;
}

.msg.ok {
  color: #16a34a;
}
</style>
