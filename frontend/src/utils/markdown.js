import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'

/**
 * Markdown 渲染工具（D3/D4）：渲染器实例全局单例。
 * - markdown-it：html:false（禁原始 HTML，D4）
 * - 输出再过 DOMPurify 净化
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false
})

/** 渲染单块 raw（正文类块：段落/表格/列表/引用/标题） */
export function renderBlock(raw) {
  if (raw == null) return ''
  return DOMPurify.sanitize(md.render(raw))
}

/** 渲染见解正文（支持 Markdown，同样净化） */
export const renderInline = renderBlock

/** 从代码块 raw 中剥掉首尾围栏，取纯代码文本 */
export function extractCode(raw) {
  if (raw == null) return ''
  const lines = raw.split('\n')
  // 去掉首行围栏 ```lang
  if (lines.length && lines[0].trim().startsWith('```')) {
    lines.shift()
  }
  // 去掉末行围栏
  if (lines.length && lines[lines.length - 1].trim() === '```') {
    lines.pop()
  }
  return lines.join('\n')
}

/** 语法高亮；lang 未注册则不高亮（返回原文 + 是否高亮标记） */
export function highlight(code, lang) {
  if (lang && lang !== 'text' && hljs.getLanguage(lang)) {
    try {
      return { html: hljs.highlight(code, { language: lang }).value, highlighted: true }
    } catch (e) {
      // fallthrough
    }
  }
  return { html: escapeHtml(code), highlighted: false }
}

export function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export { hljs }
