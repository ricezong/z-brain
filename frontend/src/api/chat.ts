import request from '@/utils/request'
import type { Result, ChatRequest, ChatResponse } from '@/types'

export const chatApi = {
  /** 同步问答 */
  chatSync(data: ChatRequest) {
    return request.post<Result<ChatResponse>, Result<ChatResponse>>('/chat/sync', { ...data, stream: false })
  },

  /** 流式问答（SSE）- 返回 EventSource */
  chatStream(data: ChatRequest, onMessage: (event: string, data: any) => void, onError?: (err: any) => void) {
    const controller = new AbortController()

    fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({ ...data, stream: true }),
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        const reader = response.body!.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          let currentEvent = 'message'
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:') && line.length > 5) {
              const dataStr = line.slice(5).trim()
              try {
                const parsed = JSON.parse(dataStr)
                onMessage(currentEvent, parsed)
              } catch {
                onMessage(currentEvent, dataStr)
              }
              currentEvent = 'message'
            }
          }
        }
      })
      .catch((err) => {
        if (err.name !== 'AbortError' && onError) {
          onError(err)
        }
      })

    return {
      abort: () => controller.abort(),
    }
  },
}
