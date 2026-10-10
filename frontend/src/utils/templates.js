// 按题目语言生成代码模板（与后端各 LanguageHandler 的包装约定对齐）。
//
// 约定（题库统一用 Java 风格签名存储，模板按目标语言翻译）：
//   - methodSignature 统一形如 "int[] twoSum(int[], int)"，学生按所选语言写对应代码
//   - 本文件负责把 Java 类型记号翻译成 C / C++ / Python / Go 的原生类型

// 语言名 → Monaco 语言 id
export function monacoLanguage(language) {
  const l = (language || '').toLowerCase()
  if (l === 'python' || l === 'py' || l === 'python3') return 'python'
  if (l === 'go' || l === 'golang') return 'go'
  if (l === 'c') return 'c'
  if (l === 'c++' || l === 'cpp') return 'cpp'
  return 'java'
}

function langOf(q) {
  const l = (q && q.language ? String(q.language) : '').toLowerCase()
  if (l === 'python' || l === 'py' || l === 'python3') return 'python'
  if (l === 'go' || l === 'golang') return 'go'
  if (l === 'c') return 'c'
  if (l === 'c++' || l === 'cpp') return 'cpp'
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

// ===== C / C++ 类型映射（题库统一用 Java 类型记号存储，如 int[] / List<Integer>）=====

function cType(t) {
  return ({
    'void': 'void', 'int': 'int', 'long': 'long long', 'double': 'double', 'float': 'float',
    'boolean': 'bool', 'String': 'char*',
    'int[]': 'int*', 'List<Integer>': 'int*',
    'long[]': 'long long*', 'double[]': 'double*', 'float[]': 'float*',
    'boolean[]': 'bool*', 'String[]': 'char**',
    'int[][]': 'int**', 'List<List<Integer>>': 'int**',
    'double[][]': 'double**', 'String[][]': 'char***',
    'ListNode': 'struct ListNode*', 'TreeNode': 'struct TreeNode*', 'Node': 'struct Node*'
  })[t] || null
}

function cppType(t) {
  return ({
    'void': 'void', 'int': 'int', 'long': 'long long', 'double': 'double', 'float': 'float',
    'boolean': 'bool', 'String': 'string',
    'int[]': 'vector<int>', 'List<Integer>': 'vector<int>',
    'long[]': 'vector<long long>', 'double[]': 'vector<double>', 'float[]': 'vector<float>',
    'boolean[]': 'vector<bool>', 'String[]': 'vector<string>',
    'int[][]': 'vector<vector<int>>', 'List<List<Integer>>': 'vector<vector<int>>',
    'double[][]': 'vector<vector<double>>', 'String[][]': 'vector<vector<string>>',
    'ListNode': 'ListNode*', 'TreeNode': 'TreeNode*', 'Node': 'Node*'
  })[t] || null
}

function is1dArray(t) {
  return t === 'int[]' || t === 'long[]' || t === 'double[]' || t === 'float[]'
    || t === 'boolean[]' || t === 'String[]' || t === 'List<Integer>'
}

function is2dArray(t) {
  return t === 'int[][]' || t === 'double[][]' || t === 'String[][]' || t === 'List<List<Integer>>'
}

// C：Java 参数 → 形参（数组展开为「指针 + 长度」）；返回 null 表示存在不支持的类型
function cParamChunks(sig) {
  const chunks = []
  if (sig.params) {
    const parts = sig.params.split(',')
    for (let j = 0; j < parts.length; j++) {
      const p = parts[j].trim()
      if (!p) continue
      const seg = p.split(/\s+/)
      const type = seg[0]
      const ct = cType(type)
      if (!ct) return null
      const pname = seg[1] || ('arg' + j)
      if (is1dArray(type)) chunks.push(`${ct} ${pname}, int ${pname}Size`)
      else if (is2dArray(type)) chunks.push(`${ct} ${pname}, int ${pname}Size, int* ${pname}ColSize`)
      else chunks.push(`${ct} ${pname}`)
    }
  }
  return chunks
}

// C：默认返回语句（让模板开箱可编译）
function cReturnStmt(ret) {
  if (ret === 'void') return ''
  if (is1dArray(ret) || is2dArray(ret)) return '\n    *returnSize = 0;\n    return NULL;'
  if (ret === 'int' || ret === 'long' || ret === 'float' || ret === 'double') return '\n    return 0;'
  if (ret === 'boolean') return '\n    return false;'
  return '\n    return NULL;' // String / ListNode / TreeNode / Node
}

// C++：默认返回语句
function cppReturnStmt(ret) {
  if (ret === 'void') return ''
  if (is1dArray(ret) || is2dArray(ret)) return '\n        return {};'
  if (ret === 'int' || ret === 'long' || ret === 'float' || ret === 'double') return '\n        return 0;'
  if (ret === 'boolean') return '\n        return false;'
  if (ret === 'String') return '\n        return "";'
  return '\n        return nullptr;' // ListNode* / TreeNode* / Node*
}

function capitalize(s) {
  if (!s) return s
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function lowerFirst(s) {
  if (!s) return s
  return s.charAt(0).toLowerCase() + s.slice(1)
}

// 解析设计题方法定义：构造器「MyStack()」无返回值，普通方法「void push(int)」带返回值
function parseDesignSig(s) {
  const t = (s || '').trim()
  const open = t.indexOf('(')
  const close = t.lastIndexOf(')')
  if (open < 0 || close < 0 || open > close) return null
  const head = t.slice(0, open).trim()
  const params = t.slice(open + 1, close).trim()
  const seg = head.split(/\s+/)
  if (seg.length >= 2) return { ret: seg.slice(0, -1).join(' '), name: seg[seg.length - 1], params }
  return { ret: null, name: seg[0], params }
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

// ===== Java 类型 → Go / Python 类型（题库统一存 Java 风格签名，模板按目标语言翻译）=====

function goType(t) {
  return ({
    'int': 'int', 'long': 'int64', 'double': 'float64', 'float': 'float32',
    'boolean': 'bool', 'String': 'string',
    'int[]': '[]int', 'List<Integer>': '[]int',
    'long[]': '[]int64', 'double[]': '[]float64', 'boolean[]': '[]bool', 'String[]': '[]string',
    'int[][]': '[][]int', 'List<List<Integer>>': '[][]int', 'double[][]': '[][]float64', 'String[][]': '[][]string',
    'ListNode': '*ListNode', 'TreeNode': '*TreeNode', 'Node': '*Node'
  })[t] || null
}

function pyType(t) {
  return ({
    'void': 'None', 'int': 'int', 'long': 'int', 'double': 'float', 'float': 'float',
    'boolean': 'bool', 'String': 'str',
    'int[]': 'List[int]', 'List<Integer>': 'List[int]', 'long[]': 'List[int]',
    'double[]': 'List[float]', 'boolean[]': 'List[bool]', 'String[]': 'List[str]',
    'int[][]': 'List[List[int]]', 'List<List<Integer>>': 'List[List[int]]',
    'double[][]': 'List[List[float]]', 'String[][]': 'List[List[str]]',
    'ListNode': 'Optional[ListNode]', 'TreeNode': 'Optional[TreeNode]', 'Node': 'Optional[Node]'
  })[t] || null
}

// 解析 Java 风格签名里的参数类型列表（每个参数只取类型，忽略参数名）
function javaParamTypes(sig) {
  return (sig && sig.params ? sig.params.split(',') : [])
    .map(p => p.trim().split(/\s+/)[0])
    .filter(Boolean)
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
  const sig = parseJavaSig(q.methodSignature)
  if (!sig) return `# 实现方法 ${name}（评测由后端执行）\nfrom typing import List, Optional\n\nclass Solution:\n    def ${name}(self):\n        # 在这里编写你的代码\n        pass\n`
  const mapped = javaParamTypes(sig).map(pyType)
  if (mapped.some(t => !t)) return `# 实现方法 ${sig.name}（评测由后端执行）\n# 签名包含暂不支持的类型，请按题面给出的方法签名编写\n`
  const ret = sig.ret === 'void' ? null : pyType(sig.ret)
  if (sig.ret !== 'void' && !ret) return `# 实现方法 ${sig.name}（评测由后端执行）\n# 暂不支持返回值类型：${sig.ret}\n`
  const params = mapped.map((t, i) => `arg${i}: ${t}`).join(', ')
  const selfParams = params ? `self, ${params}` : 'self'
  const arrow = ret ? ` -> ${ret}` : ''
  const retLine = pythonReturn(ret || '')
  const body = retLine === 'pass' ? '        pass' : `        return ${retLine}`
  return `# 实现方法 ${sig.name}（评测由后端执行）\nfrom typing import List, Optional\n\nclass Solution:\n    def ${sig.name}(${selfParams})${arrow}:\n        # 在这里编写你的代码\n${body}\n`
}

function goMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parseJavaSig(q.methodSignature)
  if (!sig) return `// 实现方法 ${name}（评测由后端执行）\npackage main\n\nfunc ${name}() {\n    // 在这里编写你的代码\n}\n`
  const mapped = javaParamTypes(sig).map(goType)
  if (mapped.some(t => !t)) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 签名包含暂不支持的类型，请按题面给出的方法签名编写\n`
  const ret = sig.ret === 'void' ? null : goType(sig.ret)
  if (sig.ret !== 'void' && !ret) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 暂不支持返回值类型：${sig.ret}\n`
  const params = mapped.map((t, i) => `arg${i} ${t}`).join(', ')
  const retPart = ret ? ` ${ret}` : ''
  const retLine = goReturn(ret || '')
  const body = retLine == null ? '' : `\n    return ${retLine}\n`
  return `// 实现方法 ${sig.name}（评测由后端执行）\npackage main\n\nfunc ${sig.name}(${params})${retPart} {\n    // 在这里编写你的代码${body}}\n`
}

// ===== C / C++ METHOD 模式 =====

function cMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parseJavaSig(q.methodSignature)
  if (!sig) return `// 实现方法 ${name}（评测由后端执行）\n// 无法解析签名，请按题面给出的方法签名编写\n`
  const retType = cType(sig.ret)
  if (!retType) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 暂不支持返回值类型：${sig.ret}\n`
  const chunks = cParamChunks(sig)
  if (!chunks) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 签名包含暂不支持的类型，请按题面给出的方法签名编写\n`
  if (is1dArray(sig.ret)) chunks.push('int* returnSize')
  else if (is2dArray(sig.ret)) chunks.push('int* returnSize, int** returnColumnSizes')
  const params = chunks.map((c) => '    ' + c).join(',\n')
  const body = cReturnStmt(sig.ret)
  const note = is1dArray(sig.ret) || is2dArray(sig.ret) ? '；数组返回值经 returnSize 传出长度' : ''
  return `// 实现方法 ${sig.name}（评测由后端执行${note}）\n${retType} ${sig.name}(\n${params}\n) {\n    // 在这里编写你的代码${body}\n}\n`
}

function cppMethod(q) {
  const name = q.methodName || 'Solution'
  const sig = parseJavaSig(q.methodSignature)
  if (!sig) return `// 实现方法 ${name}（评测由后端执行）\n// 无法解析签名，请按题面给出的方法签名编写\n`
  const retType = cppType(sig.ret)
  if (!retType) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 暂不支持返回值类型：${sig.ret}\n`
  const chunks = []
  if (sig.params) {
    const parts = sig.params.split(',')
    for (let j = 0; j < parts.length; j++) {
      const p = parts[j].trim()
      if (!p) continue
      const seg = p.split(/\s+/)
      const type = seg[0]
      const ct = cppType(type)
      if (!ct) return `// 实现方法 ${sig.name}（评测由后端执行）\n// 签名包含暂不支持的类型，请按题面给出的方法签名编写\n`
      const pname = seg[1] || ('arg' + j)
      chunks.push(is1dArray(type) || is2dArray(type) ? `${ct}& ${pname}` : `${ct} ${pname}`)
    }
  }
  const params = chunks.join(', ')
  const body = cppReturnStmt(sig.ret)
  return `// 实现方法 ${sig.name}（评测由后端执行）\n#include <vector>\n#include <string>\nusing namespace std;\n\nclass Solution {\npublic:\n    ${retType} ${sig.name}(${params}) {\n        // 在这里编写你的代码${body}\n    }\n};\n`
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
    const sig = parseDesignSig(s)
    if (!sig) return `    # ${s}`
    const mapped = javaParamTypes(sig).map(pyType)
    if (mapped.some(t => !t)) return `    # 签名包含暂不支持的类型：${s}`
    const params = mapped.map((t, j) => `arg${j}: ${t}`).join(', ')
    const selfParams = params ? `self, ${params}` : 'self'
    // 构造器（列表第一项或方法名 == 类名）渲染为 __init__
    if (i === 0 || sig.name === name || sig.name === '__init__') {
      return `    def __init__(${selfParams}):\n        # TODO\n        pass`
    }
    const ret = sig.ret === 'void' ? null : pyType(sig.ret)
    if (sig.ret !== 'void' && !ret) return `    # 暂不支持返回值类型：${s}`
    const arrow = ret ? ` -> ${ret}` : ''
    const retLine = pythonReturn(ret || '')
    const body = retLine === 'pass' ? '        pass' : `        return ${retLine}`
    return `    def ${sig.name}(${selfParams})${arrow}:\n        # TODO\n${body}`
  }).join('\n')
  return `# 实现 ${name} 类（评测由后端执行）\nclass ${name}:\n${methods}\n`
}

function goDesign(q) {
  const name = q.methodName || 'Solution'
  const methods = (q.designMethods || []).map((s, i) => {
    const sig = parseDesignSig(s)
    if (!sig) return `// ${s}`
    const mapped = javaParamTypes(sig).map(goType)
    if (mapped.some(t => !t)) return `// 签名包含暂不支持的类型：${s}`
    const params = mapped.map((t, j) => `arg${j} ${t}`).join(', ')
    if (i === 0 || sig.name === name) {
      // 构造器：包装按 func <name>(...) <name> 调用
      return `func ${name}(${params}) ${name} {\n    // TODO\n    return ${name}{}\n}`
    }
    const ret = sig.ret === 'void' ? null : goType(sig.ret)
    if (sig.ret !== 'void' && !ret) return `// 暂不支持返回值类型：${s}`
    const retPart = ret ? ` ${ret}` : ''
    const retLine = goReturn(ret || '')
    const body = retLine == null ? '' : `\n    return ${retLine}\n`
    return `func (x *${name}) ${sig.name}(${params})${retPart} {\n    // TODO${body}}`
  }).join('\n\n')
  return `// 实现 ${name} 类（评测由后端执行）\npackage main\n\ntype ${name} struct {\n}\n\n${methods}\n`
}

// ===== C / C++ DESIGN 模式 =====

function cDesign(q) {
  const defs = (q.designMethods || []).map((s) => parseDesignSig(s))
  const first = defs.find((d) => d) || { name: 'Solution' }
  const cls = (q.methodName && q.methodName.trim()) || first.name
  const prefix = lowerFirst(cls)
  const blocks = defs.map((sig, i) => {
    if (!sig) return `// 无法解析：${q.designMethods[i]}`
    const chunks = []
    if (sig.params) {
      const parts = sig.params.split(',')
      for (let j = 0; j < parts.length; j++) {
        const p = parts[j].trim()
        if (!p) continue
        const seg = p.split(/\s+/)
        const type = seg[0]
        const ct = cType(type)
        if (!ct) return `// 签名包含暂不支持的类型：${q.designMethods[i]}`
        const pname = seg[1] || ('arg' + j)
        if (is1dArray(type)) chunks.push(`${ct} ${pname}, int ${pname}Size`)
        else if (is2dArray(type)) chunks.push(`${ct} ${pname}, int ${pname}Size, int* ${pname}ColSize`)
        else chunks.push(`${ct} ${pname}`)
      }
    }
    if (i === 0) {
      return `${cls}* ${prefix}Create(${chunks.join(', ')}) {\n    // TODO: 初始化\n    ${cls}* obj = (${cls}*)malloc(sizeof(${cls}));\n    return obj;\n}`
    }
    const retType = cType(sig.ret)
    if (!retType) return `// 暂不支持返回值类型：${q.designMethods[i]}`
    const fnName = `${prefix}${capitalize(sig.name)}`
    const extra = is1dArray(sig.ret) ? ', int* returnSize' : is2dArray(sig.ret) ? ', int* returnSize, int** returnColumnSizes' : ''
    const self = chunks.length ? `, ${chunks.join(', ')}` : ''
    const body = cReturnStmt(sig.ret)
    return `${retType} ${fnName}(${cls}* obj${self}${extra}) {\n    // 在这里编写你的代码${body}\n}`
  })
  return `// 实现 ${cls}（评测由后端执行；C 设计题按 <前缀>Create / <前缀>方法 / <前缀>Free 命名）\n#include <stdlib.h>\n\ntypedef struct {\n    // TODO: 在这里定义你的字段\n} ${cls};\n\n${blocks.join('\n\n')}\n\nvoid ${prefix}Free(${cls}* obj) {\n    free(obj);\n}\n`
}

function cppDesign(q) {
  const defs = (q.designMethods || []).map((s) => parseDesignSig(s))
  const first = defs.find((d) => d) || { name: 'Solution' }
  const cls = (q.methodName && q.methodName.trim()) || first.name
  const blocks = defs.map((sig, i) => {
    if (!sig) return `    // 无法解析：${q.designMethods[i]}`
    const chunks = []
    if (sig.params) {
      const parts = sig.params.split(',')
      for (let j = 0; j < parts.length; j++) {
        const p = parts[j].trim()
        if (!p) continue
        const seg = p.split(/\s+/)
        const type = seg[0]
        const ct = cppType(type)
        if (!ct) return `    // 签名包含暂不支持的类型：${q.designMethods[i]}`
        const pname = seg[1] || ('arg' + j)
        chunks.push(is1dArray(type) || is2dArray(type) ? `${ct}& ${pname}` : `${ct} ${pname}`)
      }
    }
    const params = chunks.join(', ')
    if (i === 0) {
      return `    ${cls}(${params}) {\n        // TODO: 初始化\n    }`
    }
    const retType = cppType(sig.ret)
    if (!retType) return `    // 暂不支持返回值类型：${q.designMethods[i]}`
    const body = cppReturnStmt(sig.ret)
    return `    ${retType} ${sig.name}(${params}) {\n        // 在这里编写你的代码${body}\n    }`
  })
  return `// 实现 ${cls}（评测由后端执行）\n#include <vector>\n#include <string>\nusing namespace std;\n\nclass ${cls} {\npublic:\n${blocks.join('\n\n')}\n};\n`
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

// ===== C / C++ STDIO 模式 =====

function cStdio() {
  return `// 标准输入输出模式\n#include <stdio.h>\n\nint main() {\n    // 在这里编写你的代码\n    // 读取输入可用 scanf / fgets\n    return 0;\n}\n`
}

function cppStdio() {
  return `// 标准输入输出模式\n#include <iostream>\nusing namespace std;\n\nint main() {\n    // 在这里编写你的代码\n    // 读取输入可用 cin >> ...\n    return 0;\n}\n`
}

/** 根据题目（judgeMode / methodName / methodSignature / designMethods / language）生成默认代码模板。 */
export function defaultTemplate(q) {
  const lang = langOf(q)
  const mode = (q && q.judgeMode) || 'METHOD'
  if (mode === 'DESIGN') {
    if (lang === 'python') return pythonDesign(q)
    if (lang === 'go') return goDesign(q)
    if (lang === 'c') return cDesign(q)
    if (lang === 'cpp') return cppDesign(q)
    return javaDesign(q)
  }
  if (mode === 'STDIO') {
    if (lang === 'python') return pythonStdio()
    if (lang === 'go') return goStdio()
    if (lang === 'c') return cStdio()
    if (lang === 'cpp') return cppStdio()
    return javaStdio()
  }
  if (lang === 'python') return pythonMethod(q)
  if (lang === 'go') return goMethod(q)
  if (lang === 'c') return cMethod(q)
  if (lang === 'cpp') return cppMethod(q)
  return javaMethod(q)
}
