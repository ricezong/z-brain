<template>
  <div class="markdown-view">
    <div
      v-for="block in blockCache"
      :key="block.index"
      v-memo="[block.signature]"
      v-html="block.html"
    ></div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import taskListPlugin from 'markdown-it-task-lists'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const props = defineProps({
  content: { type: String, default: '' },
  citations: { type: Array, default: () => [] },
  isStreaming: { type: Boolean, default: false }
})

const emit = defineEmits(['rendered'])

/* ======================== 辅助函数 ======================== */

function escapeHtml(text) {
  const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }
  return text.replace(/[&<>"']/g, m => map[m])
}

function highlightCode(code, lang) {
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const langLabel = lang || 'text'
  const header = `<div class="code-block-header"><span class="code-lang">${escapeHtml(langLabel)}</span><button class="code-copy-btn">复制</button></div>`
  try {
    const highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value
    return `<pre class="hljs code-block-wrapper">${header}<code>${highlighted}</code></pre>`
  } catch {
    return `<pre class="hljs code-block-wrapper">${header}<code>${escapeHtml(code)}</code></pre>`
  }
}

/* ======================== Markdown 解析器 ======================== */

/** 流式渲染 Markdown 解析器（宽松模式：不转 br，容错解析） */
const mdStream = new MarkdownIt({
  html: false, linkify: true, typographer: true, breaks: false,
  highlight: highlightCode
}).use(taskListPlugin, { enabled: true })

/** 终态渲染 Markdown 解析器（严格模式） */
const mdFinal = new MarkdownIt({
  html: false, linkify: true, typographer: true, breaks: true,
  highlight: highlightCode
}).use(taskListPlugin, { enabled: true })

/* ======================== 文本预处理 ======================== */

const CITATION_RE = /\[?(doc_\d+)\]?/g
const CITE_PREFIX = '@@ZBRAIN_CITE_'
const CITE_SUFFIX = '@@'

/** 修复 LLM 常见的 Markdown 语法问题 */
function fixMarkdown(text) {
  let normalized = text.replace(/^#{1,6}(?!\s)/gm, match => match + ' ')
  normalized = normalized.replace(/^[-*](?!\s)/gm, match => match + ' ')
  normalized = normalized.replace(/^\d+\.(?!\s)/gm, match => match + ' ')
  normalized = normalized.replace(/^>(?!\s)/gm, match => match + ' ')
  return normalized
}

/** 流式渲染时自动补全未闭合的 Markdown 块 */
function autoCloseIncomplete(text) {
  // 补全未闭合的代码围栏 ```
  const fenceCount = (text.match(/^```/gm) || []).length
  if (fenceCount % 2 !== 0) {
    text += '\n```'
  }
  // 补全未闭合的行内代码 `
  const inlineCodeCount = (text.match(/`/g) || []).length
  if (inlineCodeCount % 2 !== 0) {
    text += '`'
  }
  // 补全未完成的表格：检测末尾有 | 开头的行但缺少分隔行
  const lines = text.split('\n')
  let lastPipeIdx = -1
  for (let i = lines.length - 1; i >= 0; i--) {
    if (lines[i].trim().startsWith('|')) {
      lastPipeIdx = i
    } else if (lines[i].trim() !== '') {
      break
    }
  }
  if (lastPipeIdx !== -1) {
    let hasSeparator = false
    let firstRowIdx = lastPipeIdx
    for (let i = lastPipeIdx; i >= 0; i--) {
      if (!lines[i].trim().startsWith('|')) break
      firstRowIdx = i
      if (/^\|[\s\-:|]+\|?\s*$/.test(lines[i].trim()) && lines[i].includes('-')) {
        hasSeparator = true
        break
      }
    }
    if (!hasSeparator) {
      const colCount = Math.max(1, (lines[firstRowIdx].match(/\|/g) || []).length - 1)
      lines.splice(firstRowIdx + 1, 0, '|' + '---|'.repeat(colCount))
      text = lines.join('\n')
    }
  }
  return text
}

/* ======================== 块级缓存引擎 ======================== */

/**
 * 将 markdown-it 的扁平 token 数组按顶层块分组。
 * 每个组包含完整的 open…close token 对（或单个原子 token 如 fence、hr）。
 */
function groupTokens(tokens) {
  const groups = []
  let current = []
  let depth = 0

  for (const token of tokens) {
    current.push(token)
    if (token.type.endsWith('_open')) depth++
    if (token.type.endsWith('_close')) depth--

    if (depth === 0 && current.length > 0) {
      groups.push(current)
      current = []
    }
  }
  if (current.length > 0) groups.push(current)
  return groups
}

/** 生成块签名：type + content + info，用于 v-memo 依赖比较 */
function getBlockSignature(group, isStreaming) {
  const suffix = isStreaming ? '_s' : '_f'
  return group.map(t => `${t.type}:${t.content || ''}:${t.info || ''}`).join('|') + suffix
}

/** 渲染单个 token 组为 HTML */
function renderGroup(group, mdInstance) {
  const env = {}
  return mdInstance.renderer.render(group, mdInstance.options, env)
}

/* ======================== 状态 ======================== */

const blockCache = ref([])
let debounceTimer = null
let lastProcessedKey = ''

/* ======================== 核心处理 ======================== */

function processContent() {
  const { content, citations, isStreaming } = props

  // 去重：相同 content + isStreaming + citations 状态不重复处理
  const processKey = content + '|' + isStreaming + '|' + (citations?.length || 0)
  if (processKey === lastProcessedKey) return
  lastProcessedKey = processKey

  if (!content) {
    blockCache.value = []
    emit('rendered')
    return
  }

  // 1. 预处理：修复 LLM 常见 Markdown 语法缺陷
  let normalized = fixMarkdown(content)

  // 2. 流式态：自动补全未闭合块
  if (isStreaming) {
    normalized = autoCloseIncomplete(normalized)
  }

  // 3. 引用标记前置处理：替换为占位符，避免在 HTML 上做正则替换
  const citePlaceholders = []
  if (citations?.length > 0) {
    const labels = new Set(citations.map(c => c.label))
    normalized = normalized.replace(CITATION_RE, (match, label) => {
      if (labels.has(label)) {
        const idx = citePlaceholders.length
        citePlaceholders.push(label)
        return `${CITE_PREFIX}${idx}${CITE_SUFFIX}`
      }
      return match
    })
  }

  // 4. Markdown → tokens → 顶层块分组
  const mdInstance = isStreaming ? mdStream : mdFinal
  const tokens = mdInstance.parse(normalized, {})
  const groups = groupTokens(tokens)

  // 5. 逐块渲染（带缓存命中检测）
  const newBlocks = []
  const oldCache = blockCache.value

  for (let i = 0; i < groups.length; i++) {
    const group = groups[i]
    const signature = getBlockSignature(group, isStreaming)

    // 缓存命中：复用已渲染的块
    if (oldCache[i] && oldCache[i].signature === signature) {
      newBlocks.push(oldCache[i])
    } else {
      // 缓存未命中：渲染新块
      let html = renderGroup(group, mdInstance)

      // 还原引用占位符为可点击链接
      citePlaceholders.forEach((label, idx) => {
        const linkHtml = `<a class="citation-ref" data-citation="${label}" href="javascript:void(0)">${label}</a>`
        html = html.split(`${CITE_PREFIX}${idx}${CITE_SUFFIX}`).join(linkHtml)
      })

      newBlocks.push({ index: i, html, signature })
    }
  }

  blockCache.value = newBlocks
  emit('rendered')
}

/* ======================== Watchers ======================== */

// 内容变化：流式态防抖，终态立即渲染
watch(() => props.content, () => {
  if (props.isStreaming) {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(processContent, 80)
  } else {
    processContent()
  }
}, { immediate: true })

// 流式结束：清除防抖，立即做一次严格渲染
watch(() => props.isStreaming, (newVal, oldVal) => {
  if (oldVal && !newVal) {
    clearTimeout(debounceTimer)
    lastProcessedKey = '' // 强制重新处理（切换解析器）
    processContent()
  }
})

// 引用数据变化：重新处理（引用占位符依赖 citations）
watch(() => props.citations, () => {
  if (props.content) {
    clearTimeout(debounceTimer)
    lastProcessedKey = '' // 强制重新处理
    debounceTimer = setTimeout(processContent, 50)
  }
})
</script>
