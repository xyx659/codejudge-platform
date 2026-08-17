import { del, download, get, post, put, upload } from './http'

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

export function listQuestions(params = {}) {
  return get(`/admin/questions${query(params)}`)
}

export function getQuestion(id) {
  return get(`/admin/questions/${id}`)
}

export function createQuestion(data) {
  return post('/admin/questions', data)
}

export function updateQuestion(id, data) {
  return put(`/admin/questions/${id}`, data)
}

export function deleteQuestion(id) {
  return del(`/admin/questions/${id}`)
}

export function addTestCase(id, data) {
  return post(`/admin/questions/${id}/test-cases`, data)
}

export function updateTestCase(id, index, data) {
  return put(`/admin/questions/${id}/test-cases/${index}`, data)
}

export function deleteTestCase(id, index) {
  return del(`/admin/questions/${id}/test-cases/${index}`)
}

export function publishQuestion(id, published) {
  return put(`/admin/questions/${id}/publish?published=${published}`)
}

export function searchExternal(params = {}) {
  return get(`/admin/questions/external/search${query(params)}`)
}

export function importExternal(data) {
  return post('/admin/questions/external/import', data)
}

export function importQuestionTemplate(file) {
  return upload('/admin/questions/import-template', file)
}

export function downloadQuestionTemplate() {
  return download(
    '/admin/questions/import-template',
    'question-import-template.json'
  )
}
