package cn.kong.eon.agent.orchestrator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 对话 Controller（唯一对话端点）
 *
 * <p>端点：{@code POST /api/agent/chat/stream}（SSE 流式）</p>
 *
 * <p>新工程废弃旧 /api/chat/stream（RAGEngine/ChitchatEngine/IntentRouter），
 * 统一走 Agent Loop（ReAct）：ChatClient + Advisor 链 + 工具循环。</p>
 *
 * @author eon-team
 */
@Slf4j
@Tag(name = "Agent 个人助手")
@RestController
@RequestMapping("/agent/chat")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator orchestrator;

    @Operation(summary = "Agent 流式对话（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            SseEmitter emitter = new SseEmitter();
            emitter.complete();
            return emitter;
        }
        return orchestrator.stream(request);
    }
}
