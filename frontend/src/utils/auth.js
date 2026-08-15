// 认证信息本地存储：token 与用户信息存于 localStorage，供请求拦截器与路由守卫读取
const TOKEN_KEY = 'codejudge_token'
const USER_KEY = 'codejudge_user'

/** 获取 token */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存 token 与用户信息 */
export function setAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 获取当前登录用户信息 */
export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  try {
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

/** 清除登录状态 */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
