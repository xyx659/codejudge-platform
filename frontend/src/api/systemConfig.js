import { get, put } from './http'

/** 查询系统配置 */
export function getSystemConfig() {
  return get('/admin/system-config')
}

/** 更新评测、AI 和限流配置 */
export function updateSystemConfig(data) {
  return put('/admin/system-config', data)
}
