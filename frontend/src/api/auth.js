// 认证相关接口封装
import { post } from './http'

/** 登录，成功后返回 { token, username, name, role }；role 决定后端查哪张用户表 */
export function login(username, password, role) {
  return post('/auth/login', { username, password, role })
}
