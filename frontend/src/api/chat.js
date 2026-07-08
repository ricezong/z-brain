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

  // content 事件队列：逐帧分发，实现打字机效果
  let contentQueue = []
  let contentFlushing = false

  function flushContent() {
    if (contentQueue.length === 0) {
      contentFlushing = false
      return
    }
    contentFlushing = true
    // 批量合并：将队列中所有 chunk 合并为一次更新，大幅减少渲染次数
    const batch = contentQueue.join('')
    contentQueue = []
    onMessage({ type: 'content', data: batch })
    requestAnimationFrame(flushContent)
  }

  function enqueueContent(chunk) {
    contentQueue.push(chunk)
    if (!contentFlushing) {
      flushContent()
    }
  }

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
              // SSE 规范：多个 data: 行之间用 \n 连接
              const dataLine = line.slice(5)
              const value = dataLine.startsWith(' ') ? dataLine.slice(1) : dataLine
              if (eventData.length > 0) {
                eventData += '\n' + value
              } else {
                eventData = value
              }
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

          // done 事件：先 flush 剩余 content，再回调
          if (currentEvent === 'done') {
            // 等待队列中剩余 content 全部渲染完
            const finish = () => {
              if (contentQueue.length > 0) {
                requestAnimationFrame(finish)
              } else {
                onDone && onDone(parsed)
              }
            }
            finish()
            return
          }

          // error 事件
          if (currentEvent === 'error') {
            onError && onError(new Error(typeof parsed === 'string' ? parsed : 'SSE error'))
            return
          }

          // content 事件入队，逐帧分发
          if (currentEvent === 'content') {
            enqueueContent(parsed)
          } else {
            // 其他事件即时回调
            onMessage && onMessage({ type: currentEvent, data: parsed })
          }
        }
      }

      // 流结束但未收到 done 事件
      const finish = () => {
        if (contentQueue.length > 0) {
          requestAnimationFrame(finish)
        } else {
          onDone && onDone()
        }
      }
      finish()
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
