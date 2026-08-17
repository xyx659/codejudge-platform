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
