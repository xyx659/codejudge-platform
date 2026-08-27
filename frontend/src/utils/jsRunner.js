// 本地样例自测：把学生代码在浏览器内（Web Worker 隔离）跑一遍样例测试用例，
// 返回每例通过/失败与实际输出。不提交、不占用后端评测。
//
// 支持两种写法：
//   1. 直接写 JavaScript（function sum(...) {...} / const sum = (...) => ...）；
//   2. Java 的 Solution 类（public int sum(int a,int b){...}）——会做一次轻量转换。
// 输入按空白切分，数字自动转 number，其余按字符串；比对 String(actual).trim() === expected.trim()。

const WORKER_SOURCE = `
function javaMethodToJs(source, methodName) {
  // 找到方法签名：methodName( 参数 ) {
  var sig = new RegExp('\\\\b' + methodName + '\\\\s*\\\\(([^)]*)\\\\)\\\\s*\\\\{');
  var m = source.match(sig);
  if (!m) return null;
  // 参数名取每个参数声明里的最后一个标识符（去掉类型/修饰符）
  var params = (m[1] || '').split(',').filter(function (s) { return s.trim(); }).map(function (s) {
    var tokens = s.trim().split(/\\s+/);
    return tokens[tokens.length - 1];
  });
  // 从 { 开始做括号配对，截取完整方法体
  var start = m.index + m[0].length - 1;
  var depth = 0;
  for (var i = start; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}') {
      depth--;
      if (depth === 0) {
        return 'function ' + methodName + '(' + params.join(',') + ') ' + source.slice(start, i + 1);
      }
    }
  }
  return null;
}

self.onmessage = function (e) {
  var data = e.data;
  var sourceCode = data.sourceCode;
  var methodName = data.methodName;
  var testCases = data.testCases;

  // 先按纯 JS 解析（函数声明 / 箭头函数 / var 赋值）
  var fn = null;
  try {
    fn = new Function(sourceCode + '\\n; return typeof ' + methodName + ' !== "undefined" ? ' + methodName + ' : null;')();
  } catch (err) {
    fn = null;
  }

  // JS 解析不到，再按 Java 的 Solution 类做轻量转换
  if (typeof fn !== 'function') {
    var js = javaMethodToJs(sourceCode, methodName);
    if (!js) {
      self.postMessage({ compileError: '未找到方法 ' + methodName + '，请确认方法名与题目一致' });
      return;
    }
    try {
      fn = new Function(js + '; return ' + methodName + ';')();
    } catch (err2) {
      self.postMessage({ compileError: '代码编译失败：' + (err2 && err2.message ? err2.message : String(err2)) });
      return;
    }
  }

  if (typeof fn !== 'function') {
    self.postMessage({ compileError: '未找到方法 ' + methodName + '，请确认方法名与题目一致' });
    return;
  }

  var results = [];
  for (var i = 0; i < testCases.length; i++) {
    var tc = testCases[i];
    var start = performance.now();
    try {
      var args = tc.input.split(/\\s+/).filter(Boolean).map(function (t) {
        return t !== '' && !isNaN(Number(t)) ? Number(t) : t;
      });
      var actual = fn.apply(null, args);
      var actualStr = actual === undefined ? 'undefined' : String(actual).trim();
      var passed = actualStr === String(tc.expected).trim();
      results.push({
        name: tc.name,
        passed: passed,
        actual: actualStr,
        expected: String(tc.expected),
        message: passed ? '通过' : '与期望输出不符',
        durationMs: Math.round(performance.now() - start)
      });
    } catch (err) {
      results.push({
        name: tc.name,
        passed: false,
        actual: '',
        expected: String(tc.expected),
        message: err && err.message ? err.message : String(err),
        durationMs: Math.round(performance.now() - start)
      });
    }
  }
  self.postMessage({ results: results });
};
`

/**
 * 在 Web Worker 里执行学生代码，跑样例测试用例。
 *
 * @param {string} sourceCode 学生源码
 * @param {string} methodName 题目要求实现的方法名
 * @param {Array<{name:string, input:string, expected:string}>} testCases 样例测试用例
 * @param {number} [timeoutMs=3000] 超时时间（毫秒），防死循环
 * @returns {Promise<{compileError?:string, results?:Array}>} 编译错误，或每例结果
 */
export function runLocalTests(sourceCode, methodName, testCases, timeoutMs = 3000) {
  return new Promise((resolve) => {
    let settled = false
    let worker
    let timer
    const url = URL.createObjectURL(new Blob([WORKER_SOURCE], { type: 'application/javascript' }))
    const finish = (payload) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (worker) worker.terminate()
      URL.revokeObjectURL(url)
      resolve(payload)
    }
    worker = new Worker(url)
    timer = setTimeout(() => finish({ compileError: '运行超时（可能存在死循环）' }), timeoutMs)
    worker.onmessage = (e) => finish(e.data)
    worker.onerror = (e) => finish({ compileError: e.message || '运行出错' })
    worker.postMessage({ sourceCode, methodName, testCases })
  })
}
