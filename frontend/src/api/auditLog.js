import { download, get } from './http'

function queryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  })
  return query.toString()
}

/** 查询操作审计日志 */
export function listAuditLogs(filters = {}) {
  const query = queryString(filters)
  return get(`/admin/audit-logs${query ? `?${query}` : ''}`)
}

/** 下载当前筛选条件下的审计日志 CSV */
export function downloadAuditLogs(filters = {}) {
  const query = queryString(filters)
  return download(
    `/admin/audit-logs/export${query ? `?${query}` : ''}`,
    'audit-logs.csv'
  )
}
