// 后端接口统一封装。BASE_URL 为 /api，开发环境由 Vite 代理到后端 8080 端口。
import { getToken, clearAuth } from '../utils/auth'

const BASE_URL = '/api'

/**
 * 发起请求并统一处理错误、附带认证 token。
 * @param {string} path 接口路径（以 / 开头）
 * @param {object} options fetch 配置项
 * @returns {Promise<object>} 后端返回的 { code, message, data } 结构
 */
async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  // 已登录则自动附带 token
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...options, headers })

  // 登录接口的 401 属于"密码错误"，交下方统一抛错，不触发跳转
  const isLogin = path === '/auth/login'
  if (response.status === 401 && !isLogin) {
    clearAuth()
    if (!window.location.pathname.endsWith('/login')) {
      const seg = window.location.pathname.split('/')[1]
      const loginPath = ['student', 'teacher', 'admin'].includes(seg) ? `/${seg}/login` : '/student/login'
      window.location.href = loginPath
    }
    throw new Error('登录已过期，请重新登录')
  }

  let body = null
  try {
    body = await response.json()
  } catch (e) {
    // 忽略非 JSON 响应
  }

  if (!response.ok) {
    throw new Error((body && body.message) || `请求失败：${response.status}`)
  }
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

/** PUT 请求（JSON 请求体） */
export function put(path, data) {
  return request(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
}

/** DELETE 请求 */
export function del(path) {
  return request(path, { method: 'DELETE' })
}

/** multipart 文件上传请求 */
export function upload(path, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request(path, {
    method: 'POST',
    body: formData
  })
}

/** 带 JWT 下载文件并触发浏览器保存 */
export async function download(path, filename) {
  const headers = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, { headers })
  if (!response.ok) {
    throw new Error(`文件下载失败：${response.status}`)
  }

  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
