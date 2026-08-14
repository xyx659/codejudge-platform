// 后端接口统一封装。BASE_URL 为 /api，开发环境由 Vite 代理到后端 8080 端口。
const BASE_URL = '/api'

/**
 * 发起请求并统一处理错误。
 * @param {string} path 接口路径（以 / 开头）
 * @param {object} options fetch 配置项
 * @returns {Promise<object>} 后端返回的 { code, message, data } 结构
 */
async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options)
  // HTTP 层错误（404/500 等）
  if (!response.ok) {
    throw new Error(`请求失败：${response.status}`)
  }
  const body = await response.json()
  // 业务层错误（code 非 0）
  if (body && body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body
}

/** GET 请求 */
export function get(path) {
  return request(path)
}

/** POST 请求（JSON 请求体） */
export function post(path, data) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
}
