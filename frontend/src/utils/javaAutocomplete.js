// Java 自动补全 + 自动导包（轻量方案）。
//
// Monaco 本身只有 Java 的「语法高亮」，没有 Java 语言服务，因此这里用内置的映射
// 补齐 IDE 式的补全：
//   1. 关键字补全（return / if / for / int / public…）；
//   2. 常见类名补全 + 自动导包（如 ArrayList → 自动 import java.util.ArrayList）；
//   3. 常用代码片段（main / for / if / sout…）。
//
// 说明：java.lang 包（String / Math / Integer…）Java 会隐式导入，不需要写 import，
// 因此这些类只补全类名、不插入 import 行；其余类才自动插入 import。
import * as monaco from 'monaco-editor'

// 常见类名 -> 全限定名（值以 java.lang. 开头的无需 import，仅补全）
const COMMON_CLASSES = {
  // —— java.util ——
  ArrayList: 'java.util.ArrayList',
  LinkedList: 'java.util.LinkedList',
  HashMap: 'java.util.HashMap',
  LinkedHashMap: 'java.util.LinkedHashMap',
  TreeMap: 'java.util.TreeMap',
  HashSet: 'java.util.HashSet',
  LinkedHashSet: 'java.util.LinkedHashSet',
  TreeSet: 'java.util.TreeSet',
  List: 'java.util.List',
  Map: 'java.util.Map',
  Set: 'java.util.Set',
  Queue: 'java.util.Queue',
  Deque: 'java.util.Deque',
  Stack: 'java.util.Stack',
  Vector: 'java.util.Vector',
  Arrays: 'java.util.Arrays',
  Collections: 'java.util.Collections',
  Comparator: 'java.util.Comparator',
  Iterator: 'java.util.Iterator',
  Scanner: 'java.util.Scanner',
  Random: 'java.util.Random',
  Optional: 'java.util.Optional',
  Date: 'java.util.Date',

  // —— java.io ——
  BufferedReader: 'java.io.BufferedReader',
  BufferedWriter: 'java.io.BufferedWriter',
  FileReader: 'java.io.FileReader',
  FileWriter: 'java.io.FileWriter',
  File: 'java.io.File',
  InputStream: 'java.io.InputStream',
  OutputStream: 'java.io.OutputStream',
  IOException: 'java.io.IOException',

  // —— java.math ——
  BigInteger: 'java.math.BigInteger',
  BigDecimal: 'java.math.BigDecimal',

  // —— java.lang（无需 import，仅补全类名）——
  String: 'java.lang.String',
  StringBuilder: 'java.lang.StringBuilder',
  StringBuffer: 'java.lang.StringBuffer',
  Math: 'java.lang.Math',
  Integer: 'java.lang.Integer',
  Long: 'java.lang.Long',
  Double: 'java.lang.Double',
  Boolean: 'java.lang.Boolean',
  Character: 'java.lang.Character',
  Object: 'java.lang.Object',
  Comparable: 'java.lang.Comparable'
}

// Java 关键字与字面量（补全用，无语言服务时也要能补出 return/if/for 等）
const JAVA_KEYWORDS = [
  'abstract', 'assert', 'break', 'case', 'catch', 'class', 'const', 'continue',
  'default', 'do', 'else', 'enum', 'extends', 'final', 'finally', 'for',
  'if', 'implements', 'import', 'instanceof', 'interface', 'native', 'new',
  'package', 'private', 'protected', 'public', 'return', 'static', 'strictfp',
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient',
  'try', 'void', 'volatile', 'while',
  'boolean', 'byte', 'char', 'double', 'float', 'int', 'long', 'short',
  'var', 'record', 'yield',
  'true', 'false', 'null'
]

// 常用代码片段：label 为触发词，insertText 用 ${n} 占位、${0} 为最终光标位置
const JAVA_SNIPPETS = [
  { label: 'main', detail: 'main 方法', insertText: 'public static void main(String[] args) {\n\t${0}\n}' },
  { label: 'for', detail: 'for 循环', insertText: 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t${0}\n}' },
  { label: 'foreach', detail: '增强 for 循环', insertText: 'for (${1:类型} ${2:变量} : ${3:集合}) {\n\t${0}\n}' },
  { label: 'if', detail: 'if 语句', insertText: 'if (${1:条件}) {\n\t${0}\n}' },
  { label: 'ifelse', detail: 'if-else 语句', insertText: 'if (${1:条件}) {\n\t${0}\n} else {\n\t\n}' },
  { label: 'while', detail: 'while 循环', insertText: 'while (${1:条件}) {\n\t${0}\n}' },
  { label: 'sout', detail: '打印输出', insertText: 'System.out.println(${0});' },
  { label: 'try', detail: 'try-catch', insertText: 'try {\n\t${0}\n} catch (${1:Exception} ${2:e}) {\n\t\n}' }
]

// 判断某个全限定名是否需要显式 import（java.lang 包不需要）
function needsImport(fqcn) {
  return !fqcn.startsWith('java.lang.')
}

/**
 * 注册 Java 自动补全（关键字 + 类名自动导包 + 代码片段）。只需调用一次
 * （在 monaco.js 模块加载时调用）。
 */
export function registerJavaAutocomplete() {
  monaco.languages.registerCompletionItemProvider('java', {
    provideCompletionItems(model, position) {
      // 光标紧跟在 '.' 之后，说明正在输入成员/方法而非类名或关键字，不提示
      const before = model.getValueInRange(
        new monaco.Range(position.lineNumber, 1, position.lineNumber, position.column)
      )
      if (before.trimEnd().endsWith('.')) {
        return { suggestions: [] }
      }

      // 当前正在输入的单词范围，接受补全时会替换它（如 ret -> return）
      const word = model.getWordUntilPosition(position)
      const range = new monaco.Range(
        position.lineNumber,
        word.startColumn,
        position.lineNumber,
        word.endColumn
      )

      const fullText = model.getValue()
      const suggestions = []

      // 1) 关键字补全
      for (const kw of JAVA_KEYWORDS) {
        suggestions.push({
          label: kw,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: kw,
          range,
          filterText: kw,
          sortText: '0' + kw
        })
      }

      // 2) 类名补全 + 自动导包
      for (const [name, fqcn] of Object.entries(COMMON_CLASSES)) {
        const additionalTextEdits = []
        if (needsImport(fqcn) && !fullText.includes(`import ${fqcn};`)) {
          // 文件头插入 import（范围锁定第一行第一列）
          additionalTextEdits.push({
            range: new monaco.Range(1, 1, 1, 1),
            text: `import ${fqcn};\n`
          })
        }
        suggestions.push({
          label: name,
          kind: monaco.languages.CompletionItemKind.Class,
          detail: needsImport(fqcn) ? fqcn : 'java.lang（无需 import）',
          insertText: name,
          range,
          filterText: name,
          sortText: '1' + name,
          additionalTextEdits
        })
      }

      // 3) 代码片段
      for (const snip of JAVA_SNIPPETS) {
        suggestions.push({
          label: snip.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          detail: snip.detail,
          insertText: snip.insertText,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
          filterText: snip.label,
          sortText: '2' + snip.label
        })
      }

      return { suggestions }
    }
  })
}
