import { get } from './http'

function queryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  })
  return query.toString()
}

/** 查询当前数据库监控状态 */
export function getDatabaseStatus() {
  return get('/admin/db/status')
}

/** 查询数据库监控历史快照 */
export function getDatabaseHistory(params = {}) {
  const query = queryString(params)
  return get(`/admin/db/history${query ? `?${query}` : ''}`)
}
