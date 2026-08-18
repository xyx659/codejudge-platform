// Monaco 编辑器初始化：配置 worker 并导出创建编辑器的辅助函数。
//
// 要点：
//   - 加载 editor.worker（基础编辑能力）+ ts.worker（TypeScript 语言服务），
//     后者为 JavaScript 提供单词/函数/签名补全与诊断，实现「像 IDEA 一样」的补全。
//   - 用 Vite 的 `?worker` 内联 worker 文件，无需额外插件，且本地部署离线可用。
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import TsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

// Monaco 在创建 worker 时会回调 getWorker；按语言标签返回对应 worker。
self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'typescript' || label === 'javascript') {
      return new TsWorker()
    }
    return new EditorWorker()
  }
}

// 注意：不要在 Vite 环境下调用 setCompilerOptions() / setDiagnosticsOptions()。
// 它们会触发 TS worker 重建，破坏模型同步，导致 "Could not find source file" 错误
// （见 monaco-editor issue #4364）。allowJs / checkJs / 校验这些本就是 JavaScript 的默认值，
// 无需显式设置即可获得关键字、单词与函数签名补全。

/**
 * 在指定 DOM 元素上创建一个 JavaScript 代码编辑器。
 *
 * @param {HTMLElement} el 挂载容器
 * @param {{ value?: string, onChange?: (code:string)=>void }} opts 初始代码与变更回调
 * @returns {monaco.editor.IStandaloneCodeEditor} 编辑器实例（记得在组件卸载时 dispose）
 */
export function createEditor(el, { value = '', onChange } = {}) {
  const editor = monaco.editor.create(el, {
    value,
    language: 'javascript',
    theme: 'vs',
    automaticLayout: true,
    fontSize: 14,
    tabSize: 4,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    // —— 以下配置让自动补全更接近 IDE ——
    wordBasedSuggestions: 'currentDocument',
    tabCompletion: 'on',
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: 'on',
    quickSuggestions: { other: true, comments: false, strings: true },
    parameterHints: { enabled: true }
  })

  if (onChange) {
    editor.onDidChangeModelContent(() => onChange(editor.getValue()))
  }

  return editor
}

export { monaco }
