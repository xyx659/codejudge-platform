// 学生端接口封装（对应后端 /api/student/**）
import { get, post } from './http'

// 把可空参数拼成查询串，仿照 api/questions.js 的 query()
function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value)
    }
  })
  const suffix = search.toString() ? `?${search.toString()}` : ''
  return suffix
}

/** 题目列表（分页 + 难度/标签筛选） */
export function listQuestions(params = {}) {
  return get(`/student/questions${query(params)}`)
}

/** 题目详情（含测试用例） */
export function getQuestion(id) {
  return get(`/student/questions/${id}`)
}

/** 提交代码 */
export function submit(data) {
  return post('/student/submissions', data)
}

/** 提交记录列表（分页） */
export function listSubmissions(params = {}) {
  return get(`/student/submissions${query(params)}`)
}

/** 某次提交的成绩与 AI 评审 */
export function getSubmissionResult(id) {
  return get(`/student/submissions/${id}/result`)
}
