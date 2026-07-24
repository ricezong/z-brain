/**
 * Agent 流式对话（SSE）— 调用 /api/agent/chat/stream
 *
 * 事件协议与 /api/chat/stream 一致：
 *   session / content / done / error
 *
 * 回调 onMessage 收到统一格式：{ type, data }
 */
export function agentChatStream(data, { onMessage, onDone, onError }) {
  const controller = new AbortController()

  let contentQueue = []
  let contentFlushing = false

  function flushContent() {
    if (contentQueue.length === 0) {
      contentFlushing = false
      return
    }
    contentFlushing = true
    onMessage({ type: 'content', data: contentQueue.join('') })
    contentQueue = []
    requestAnimationFrame(flushContent)
  }

  fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal: controller.signal
  }).then(async (response) => {
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = 'message'

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const blocks = buffer.split('\n\n')
      buffer = blocks.pop()

      for (const block of blocks) {
        if (!block.trim()) continue
        let eventData = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const v = line.slice(5)
            eventData += (eventData.length > 0 ? '\n' : '') + (v.startsWith(' ') ? v.slice(1) : v)
          }
        }
        if (eventData === '') continue

        let parsed
        try { parsed = JSON.parse(eventData) } catch { parsed = eventData }

        if (currentEvent === 'done') {
          const finish = () => {
            if (contentQueue.length > 0) { requestAnimationFrame(finish) }
            else { onDone && onDone(parsed) }
          }
          finish()
          return
        }
        if (currentEvent === 'error') {
          onError && onError(new Error(typeof parsed === 'string' ? parsed : 'SSE error'))
          return
        }
        if (currentEvent === 'content') {
          contentQueue.push(parsed)
          if (!contentFlushing) flushContent()
        } else {
          onMessage && onMessage({ type: currentEvent, data: parsed })
        }
      }
    }

    const finish = () => {
      if (contentQueue.length > 0) { requestAnimationFrame(finish) }
      else { onDone && onDone() }
    }
    finish()
  }).catch((err) => {
    if (err.name !== 'AbortError') onError && onError(err)
  })

  return controller
}
