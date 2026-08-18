// 教师端接口封装
import { del, get, post, put } from './http'

/**
 * 组装查询字符串：把对象里非空的值拼成 ?key=value&...
 * 团队封装的 get 不带参数，所以这里手动拼 query。
 */
function qs(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  })
  const s = query.toString()
  return s ? `?${s}` : ''
}

// ===== 工作台 =====
export function getDashboardStats() {
  return get('/teacher/dashboard/stats')
}

// ===== 分类 =====
export function listCategories() {
  return get('/teacher/categories')
}
export function createCategory(data) {
  return post('/teacher/categories', data)
}
export function updateCategory(id, data) {
  return put(`/teacher/categories/${id}`, data)
}
export function deleteCategory(id) {
  return del(`/teacher/categories/${id}`)
}

// ===== 题目 =====
export function listQuestions(params = {}) {
  return get(`/teacher/questions${qs(params)}`)
}
export function getQuestion(id) {
  return get(`/teacher/questions/${id}`)
}
export function createQuestion(data) {
  return post('/teacher/questions', data)
}
export function updateQuestion(id, data) {
  return put(`/teacher/questions/${id}`, data)
}
export function deleteQuestion(id) {
  return del(`/teacher/questions/${id}`)
}
export function publishQuestion(id, published) {
  return put(`/teacher/questions/${id}/publish?published=${published}`)
}

// ===== 考试 =====
export function listExams(params = {}) {
  return get(`/teacher/exams${qs(params)}`)
}
export function getExam(id) {
  return get(`/teacher/exams/${id}`)
}
export function createExam(data) {
  return post('/teacher/exams', data)
}
export function updateExam(id, data) {
  return put(`/teacher/exams/${id}`, data)
}
export function deleteExam(id) {
  return del(`/teacher/exams/${id}`)
}
export function publishExam(id) {
  return put(`/teacher/exams/${id}/publish`)
}
export function closeExam(id) {
  return put(`/teacher/exams/${id}/close`)
}

// ===== 监考 =====
export function getMonitor(examId) {
  return get(`/teacher/monitor/${examId}`)
}

// ===== 学情分析 =====
export function getAnalytics(examId) {
  return get(`/teacher/analytics/${examId}`)
}
