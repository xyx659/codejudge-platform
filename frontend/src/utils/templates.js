// 按题目语言生成代码模板（与后端各 LanguageHandler 的包装约定对齐）。
//
// 约定（「各语言原生签名」）：
//   - Java   ：methodSignature 形如 "int[] twoSum(int[], int)"，学生写 public class Solution
//   - Python ：methodSignature 形如 "def twoSum(self, nums: List[int], target: int) -> List[int]"
//   - Go     ：methodSignature 形如 "twoSum(nums []int, target int) []int"

// 语言名 → Monaco 语言 id
export function monacoLanguage(language) {
  const l = (language || '').toLowerCase()
  if (l === 'python' || l === 'py' || l === 'python3') return 'python'
  if (l === 'go' || l === 'golang') return 'go'
  return 'java'
}

function langOf(q) {
  const l = (q && q.language ? String(q.language) : '').toLowerCase()
  if (l === 'python' || l === 'py' || l === 'python3') return 'python'
  if (l === 'go' || l === 'golang') return 'go'
  return 'java'
}

// ===== 返回值默认值（让模板开箱可编译 / 运行）=====

function javaReturn(ret) {
  if (ret === 'int' || ret === 'long' || ret === 'float' || ret === 'double') return '0'
  if (ret === 'boolean') return 'false'
  return 'null'
}

// 返回方法体语句；空返回（无返回注解 / None）返回 'pass'
function pythonReturn(ret) {
  if (!ret || ret === 'None') return 'pass'
  if (ret === 'int') return '0'
  if (ret === 'float') return '0.0'
  if (ret === 'bool') return 'False'
  if (ret === 'str') return '""'
  if (/^List\[/.test(ret) || /^Optional\[/.test(ret)) return '[]'
  return 'None'
}

// 返回 null 表示无需 return（void）
function goReturn(ret) {
  if (!ret) return null
  if (ret === 'int' || ret === 'int8' || ret === 'int16' || ret === 'int32' || ret === 'int64') return '0'
  if (ret === 'float32' || ret === 'float64') return '0'
  if (ret === 'string') return '""'
  if (ret === 'bool') return 'false'
  if (ret.startsWith('[')) return 'nil'
  if (ret.startsWith('*')) return 'nil'
  return 'nil'
}

// ===== 签名解析 =====

// Java：int[] twoSum(int[], int) → { ret, name, params }
function parseJavaSig(sig) {
  const m = (sig || '').match(/^(\S+)\s+(\w+)\((.*)\)$/)
  if (!m) return null
  return { ret: m[1], name: m[2], params: m[3] }
}

// Python：def twoSum(self, nums: List[int], target: int) -> List[int] → { name, params, ret }
function parsePythonSig(sig) {
  let s = (sig || '').trim()
  if (s.startsWith('def ')) s = s.slice(4).trim()
  const open = s.indexOf('(')
  const close = s.lastIndexOf(')')
  if (open < 0 || close < 0 || open > close) return null
  const name = s.slice(0, open).trim()
  const params = s.slice(open + 1, close).trim()
  let ret = ''
  const after = s.slice(close + 1).trim()
  if (after.startsWith('->')) ret = after.slice(2).trim()
  return { name, params, ret }
}

// Go：twoSum(nums []int, target int) []int → { name, params, ret }
function parseGoSig(sig) {
  const s = (sig || '').trim()
  const open = s.indexOf('(')
  const close = s.lastIndexOf(')')
  if (open < 0 || close < 0 || open > close) return null
  const name = s.slice(0, open).trim()
  const params = s.slice(open + 1, close).trim()
  const ret = s.slice(close + 1).trim()
  return { name, params, ret }
}

// ===== METHOD 模式 =====

function javaMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parseJavaSig(q.methodSignature)
  if (sig) {
    const ret = sig.ret, mn = sig.name, params = sig.params
    const paramDecl = params ? params.split(',').map(p => {
      const parts = p.trim().split(/\s+/)
      return `        ${parts[0]} ${parts[1] || 'arg'}`
    }).join(',\n') : ''
    const returnStmt = ret === 'void' ? '' : `\n        return ${javaReturn(ret)};`
    return `// 实现方法 ${mn}（评测由后端执行）\npublic class Solution {\n    public ${ret} ${mn}(\n${paramDecl}\n    ) {\n        // 在这里编写你的代码${returnStmt}\n    }\n}\n`
  }
  return `// 实现方法 ${name}（评测由后端执行）\npublic class Solution {\n    public Object ${name}() {\n        // 在这里编写你的代码\n        return null;\n    }\n}\n`
}

function pythonMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parsePythonSig(q.methodSignature)
  let mn = name, selfParams = 'self', ret = ''
  if (sig) {
    mn = sig.name
    let params = sig.params
    if (/^self\b/.test(params)) params = params.replace(/^self\s*,?\s*/, '')
    selfParams = params ? `self, ${params}` : 'self'
    ret = sig.ret
  }
  const arrow = ret ? ` -> ${ret}` : ''
  const retLine = pythonReturn(ret)
  const body = retLine === 'pass' ? '        pass' : `        return ${retLine}`
  return `# 实现方法 ${mn}（评测由后端执行）\nfrom typing import List, Optional\n\nclass Solution:\n    def ${mn}(${selfParams})${arrow}:\n        # 在这里编写你的代码\n${body}\n`
}

function goMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parseGoSig(q.methodSignature)
  let mn = name, params = '', ret = ''
  if (sig) {
    mn = sig.name
    params = sig.params
    ret = sig.ret
  }
  const retPart = ret ? ` ${ret}` : ''
  const retLine = goReturn(ret)
  const body = retLine == null ? '' : `\n    return ${retLine}\n`
  return `// 实现方法 ${mn}（评测由后端执行）\npackage main\n\nfunc ${mn}(${params})${retPart} {\n    // 在这里编写你的代码${body}}\n`
}

// ===== DESIGN 模式 =====

function javaDesign(q) {
  const name = q.methodName || 'Solution'
  const methods = (q.designMethods || []).map(s => {
    const sig = parseJavaSig(s)
    if (!sig) return `    // ${s}`
    const ret = sig.ret, mn = sig.name, params = sig.params
    if (mn === name) {
      const paramDecl = params ? params.split(',').map((_, i) => `        // 参数${i + 1}`).join('\n') : ''
      return `    public ${name}(${params}) {\n${paramDecl}        // TODO\n    }`
    }
    const paramDecl = params ? params.split(',').map(p => {
      const parts = p.trim().split(/\s+/)
      return `        ${parts[0]} ${parts[1] || 'arg'}`
    }).join(',\n') : ''
    return `    public ${ret} ${mn}(\n${paramDecl}\n    ) {\n        // TODO\n    }`
  }).join('\n\n')
  return `// 实现 ${name} 类（评测由后端执行）\nclass ${name} {\n${methods}\n}\n`
}

function pythonDesign(q) {
  const name = q.methodName || 'Solution'
  const methods = (q.designMethods || []).map((s, i) => {
    const sig = parsePythonSig(s)
    if (!sig) return `    # ${s}`
    // 构造器（列表第一项或方法名 == 类名）渲染为 __init__
    if (i === 0 || sig.name === name || sig.name === '__init__') {
      let params = sig.params
      if (/^self\b/.test(params)) params = params.replace(/^self\s*,?\s*/, '')
      const selfParams = params ? `self, ${params}` : 'self'
      return `    def __init__(${selfParams}):\n        # TODO\n        pass`
    }
    let params = sig.params
    if (/^self\b/.test(params)) params = params.replace(/^self\s*,?\s*/, '')
    const selfParams = params ? `self, ${params}` : 'self'
    const arrow = sig.ret ? ` -> ${sig.ret}` : ''
    const retLine = pythonReturn(sig.ret)
    const body = retLine === 'pass' ? '        pass' : `        return ${retLine}`
    return `    def ${sig.name}(${selfParams})${arrow}:\n        # TODO\n${body}`
  }).join('\n')
  return `# 实现 ${name} 类（评测由后端执行）\nclass ${name}:\n${methods}\n`
}

function goDesign(q) {
  const name = q.methodName || 'Solution'
  const methods = (q.designMethods || []).map((s, i) => {
    const sig = parseGoSig(s)
    if (!sig) return `// ${s}`
    if (i === 0 || sig.name === name) {
      // 构造器：包装按 func <name>(...) <name> 调用
      return `func ${name}(${sig.params}) ${name} {\n    // TODO\n    return ${name}{}\n}`
    }
    const retPart = sig.ret ? ` ${sig.ret}` : ''
    const retLine = goReturn(sig.ret)
    const body = retLine == null ? '' : `\n    return ${retLine}\n`
    return `func (x *${name}) ${sig.name}(${sig.params})${retPart} {\n    // TODO${body}}`
  }).join('\n\n')
  return `// 实现 ${name} 类（评测由后端执行）\npackage main\n\ntype ${name} struct {\n}\n\n${methods}\n`
}

// ===== STDIO 模式 =====

function javaStdio() {
  return `// 标准输入输出模式\nimport java.util.*;\n\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // 在这里编写你的代码\n    }\n}\n`
}

function pythonStdio() {
  return `# 标准输入输出模式\nimport sys\n\ndef main():\n    # data = sys.stdin.read()\n    # 在这里编写你的代码\n    pass\n\nif __name__ == '__main__':\n    main()\n`
}

function goStdio() {
  return `// 标准输入输出模式\npackage main\n\nfunc main() {\n    // 在这里编写你的代码\n    // 读取输入可用：data, _ := io.ReadAll(os.Stdin)（需 import "io"、"os"）\n}\n`
}

/** 根据题目（judgeMode / methodName / methodSignature / designMethods / language）生成默认代码模板。 */
export function defaultTemplate(q) {
  const lang = langOf(q)
  const mode = (q && q.judgeMode) || 'METHOD'
  if (mode === 'DESIGN') {
    if (lang === 'python') return pythonDesign(q)
    if (lang === 'go') return goDesign(q)
    return javaDesign(q)
  }
  if (mode === 'STDIO') {
    if (lang === 'python') return pythonStdio()
    if (lang === 'go') return goStdio()
    return javaStdio()
  }
  if (lang === 'python') return pythonMethod(q)
  if (lang === 'go') return goMethod(q)
  return javaMethod(q)
}
