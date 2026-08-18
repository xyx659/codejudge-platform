// 本地样例自测内核：在浏览器里跑学生写的 JavaScript 代码，不占后端。
//
// 用一个 Blob 内联的 Web Worker 执行用户代码：
//   - 与主线程隔离，代码报错/死循环不会直接卡死页面；
//   - 通过 setTimeout + worker.terminate() 实现超时兜底。
//
// 约定：学生代码里必须定义题目要求的函数（methodName），
// 输入按「空格分隔的标量」解析成实参，输出转字符串后与期望输出比对。

const WORKER_SOURCE = `
self.onmessage = function (e) {
  var payload = e.data || {}
  var sourceCode = payload.sourceCode
  var methodName = payload.methodName
  var testCases = payload.testCases || []

  try {
    // 把学生代码包成一个「返回目标函数」的函数，再取出来调用。
    // 语法：函数声明会被提升，末尾 return (methodName) 拿到那个函数。
    var fn = new Function(sourceCode + '\\n;return (' + methodName + ');')()

    if (typeof fn !== 'function') {
      self.postMessage({ error: '未找到可调用函数：' + methodName })
      return
    }

    var results = testCases.map(function (tc) {
      var start = performance.now()
      try {
        var args = parseInput(tc.input)
        var actual = String(fn.apply(null, args)).trim()
        var passed = actual === String(tc.expected).trim()
        return {
          name: tc.name,
          input: tc.input,
          expected: tc.expected,
          actual: actual,
          passed: passed,
          message: passed ? '通过' : '输出不匹配',
          durationMs: Math.round(performance.now() - start)
        }
      } catch (err) {
        return {
          name: tc.name,
          input: tc.input,
          expected: tc.expected,
          actual: '',
          passed: false,
          message: err.message || String(err),
          durationMs: Math.round(performance.now() - start)
        }
      }
    })

    self.postMessage({ results: results })
  } catch (err) {
    self.postMessage({ error: err.message || String(err) })
  }
}

// 输入解析：按空白切分，能转成数字的转数字，否则当作字符串。
function parseInput(input) {
  return String(input)
    .split(/\\s+/)
    .filter(function (t) { return t !== '' })
    .map(function (t) { return Number.isNaN(Number(t)) ? t : Number(t) })
}
`

/**
 * 在浏览器里跑样例测试用例。
 *
 * @param {string} sourceCode 学生写的完整源码（须包含 methodName 函数）
 * @param {string} methodName 题目要求的方法名（如 sum）
 * @param {Array<{name:string,input:string,expected:string}>} testCases 样例用例
 * @param {number} timeoutMs 单次整体超时（毫秒），默认 3000
 * @returns {Promise<{results?: Array, error?: string}>}
 *          成功时返回 { results }；编译错误/超时返回 { error }
 */
export function runLocalTests(sourceCode, methodName, testCases, timeoutMs = 3000) {
  return new Promise((resolve) => {
    // Worker 创建失败（如个别浏览器拦截 blob worker）时，也走 resolve 返回错误，而不是抛异常
    let worker
    let url
    try {
      const blob = new Blob([WORKER_SOURCE], { type: 'text/javascript' })
      url = URL.createObjectURL(blob)
      worker = new Worker(url)
    } catch (e) {
      resolve({ error: '运行环境初始化失败：' + (e && e.message ? e.message : e) })
      return
    }

    let settled = false
    const finish = (payload) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      worker.terminate()
      URL.revokeObjectURL(url)
      resolve(payload)
    }

    const timer = setTimeout(
      () => finish({ error: `运行超时（>${timeoutMs}ms），可能存在死循环` }),
      timeoutMs
    )

    worker.onmessage = (e) => finish(e.data || {})
    worker.onerror = (e) => finish({ error: e.message || '运行出错' })

    // testCases 可能来自 Vue 的 reactive 代理，结构化克隆无法克隆 Proxy，
    // 先做一次 JSON 深拷贝转成纯数据，再发给 Worker。
    worker.postMessage({
      sourceCode,
      methodName,
      testCases: JSON.parse(JSON.stringify(testCases || []))
    })
  })
}
