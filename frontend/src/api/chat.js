import request from './request'

/**
 * 流式问答（SSE）— 使用 fetch + ReadableStream 处理
 *
 * 后端 SseEmitter 发送的是带事件名称的 SSE，格式如下：
 *   event:session
 *   data:session-id-string
 *
 *   event:rewritten_query
 *   data:rewritten-query-string
 *
 *   event:content
 *   data:text chunk
 *
 *   event:citations
 *   data:[{"label":"doc_1","chunkId":1,"docId":2}]
 *
 *   event:done
 *   data:{"costTimeMs":1234}
 *
 * 回调 onMessage 收到的是统一格式的对象：
 *   { type: 'session', data: 'session-id-string' }
 *   { type: 'content', data: 'text chunk' }
 *   { type: 'citations', data: [{...}] }
 *   { type: 'done', data: { costTimeMs: 1234 } }
 */
export function chatStream(data, { onMessage, onDone, onError }) {
  const controller = new AbortController()

  fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal: controller.signal
  })
    .then(async (response) => {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 以空行（\n\n）分隔事件块
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() // 最后一块可能不完整，保留

        for (const block of blocks) {
          if (!block.trim()) continue

          let eventData = ''
          const lines = block.split('\n')
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              // data 行可能有多行，逐行拼接
              const dataLine = line.slice(5)
              eventData += dataLine.startsWith(' ') ? dataLine.slice(1) : dataLine
            }
          }

          if (eventData === '') continue

          // 解析 data 内容
          let parsed
          try {
            parsed = JSON.parse(eventData)
          } catch {
            // 非 JSON，保留原始字符串
            parsed = eventData
          }

          // done 事件单独处理
          if (currentEvent === 'done') {
            onDone && onDone(parsed)
            return
          }

          // error 事件
          if (currentEvent === 'error') {
            onError && onError(new Error(typeof parsed === 'string' ? parsed : 'SSE error'))
            return
          }

          // 其他事件统一回调
          onMessage && onMessage({ type: currentEvent, data: parsed })
        }
      }

      // 流结束但未收到 done 事件
      onDone && onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError && onError(err)
      }
    })

  return controller
}

/** 优化输入，增强提示词 */
export function rewriteQuery(data) {
  return request.post('/chat/rewrite', data)
}
