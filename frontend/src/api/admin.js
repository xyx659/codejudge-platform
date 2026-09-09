import { del, download, get, post, put, upload } from './http'

/** 查询用户列表 */
export function listUsers(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  })
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return get(`/admin/users${suffix}`)
}

/** 新增用户 */
export function createUser(data) {
  return post('/admin/users', data)
}

/** 修改用户资料 */
export function updateUser(role, id, data) {
  return put(`/admin/users/${role}/${id}`, data)
}

/** 修改用户角色 */
export function changeUserRole(role, id, targetRole) {
  return put(`/admin/users/${role}/${id}/role`, { targetRole })
}

/** 删除用户 */
export function deleteUser(role, id) {
  return del(`/admin/users/${role}/${id}`)
}

/** 导入用户 CSV */
export function importUsers(file) {
  return upload('/admin/users/import', file)
}

/** 下载 CSV 导入模板 */
export function downloadTemplate() {
  return download('/admin/users/import-template', 'user-import-template.csv')
}

/** 查询管理端工作台统计 */
export function getAdminDashboard() {
  return get('/admin/dashboard')
}

/** 获取当前管理员个人信息 */
export function getProfile() {
  return get('/admin/profile')
}

/** 修改管理员姓名 */
export function updateProfile(data) {
  return put('/admin/profile', data)
}

/** 修改管理员密码 */
export function changePassword(data) {
  return put('/admin/profile/password', data)
}

/** AI 根据题目描述自动生成测试用例（不入库） */
export function aiGenerateQuestion(data) {
  return post('/admin/questions/ai-generate', data)
}
