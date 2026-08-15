// 认证相关接口封装
import { post } from './http'

/** 登录，成功后返回 { token, username, name, role } */
export function login(username, password) {
  return post('/auth/login', { username, password })
}
