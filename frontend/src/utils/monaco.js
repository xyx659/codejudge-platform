// Monaco 编辑器初始化：配置 worker、注册 Java 自动导包，并导出创建编辑器的辅助函数。
//
// 要点：
//   - 加载 editor.worker（基础编辑能力）。Java 的语法高亮由 Monaco 自带，但 Monaco
//     没有 Java 语言服务，所以自动补全/自动导包由 javaAutocomplete.js 的轻量映射提供。
//   - 用 Vite 的 `?worker` 内联 worker 文件，无需额外插件，且本地部署离线可用。
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import { registerJavaAutocomplete } from './javaAutocomplete'

// Monaco 在创建 worker 时会回调 getWorker；Java 走基础 worker 即可。
self.MonacoEnvironment = {
  getWorker() {
    return new EditorWorker()
  }
}

// 注册 Java 自动补全 + 自动导包（模块加载时执行一次即可）
registerJavaAutocomplete()

/**
 * 在指定 DOM 元素上创建一个 Java 代码编辑器。
 *
 * @param {HTMLElement} el 挂载容器
 * @param {{ value?: string, readOnly?: boolean, onChange?: (code:string)=>void }} opts
 *        初始代码、是否只读与变更回调
 * @returns {monaco.editor.IStandaloneCodeEditor} 编辑器实例（记得在组件卸载时 dispose）
 */
export function createEditor(el, { value = '', readOnly = false, onChange } = {}) {
  const editor = monaco.editor.create(el, {
    value,
    language: 'java',
    theme: 'vs',
    automaticLayout: true,
    readOnly,
    fontSize: 14,
    tabSize: 4,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    // —— 以下配置让自动补全更接近 IDE ——
    wordBasedSuggestions: 'currentDocument',
    tabCompletion: 'on',
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: 'on',
    quickSuggestions: { other: true, comments: false, strings: false },
    parameterHints: { enabled: true }
  })

  if (onChange) {
    editor.onDidChangeModelContent(() => onChange(editor.getValue()))
  }

  return editor
}

export { monaco }
