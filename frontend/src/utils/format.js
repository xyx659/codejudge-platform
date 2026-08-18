// 学生端展示用的通用格式化 / 映射辅助函数

/** 难度 → 徽章配色类名 */
export function difficultyClass(d) {
  if (d === '简单') return 'easy'
  if (d === '中等') return 'medium'
  if (d === '困难') return 'hard'
  return ''
}

/** 判卷状态 → 中文文案 */
export function judgeStatusText(status) {
  const map = {
    PENDING: '评测中',
    RUN_COMPLETED: '已完成',
    COMPILE_ERROR: '编译错误',
    TIMEOUT: '超时'
  }
  return map[status] || status || '未知'
}
