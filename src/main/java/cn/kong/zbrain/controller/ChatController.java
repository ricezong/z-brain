package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.request.RewriteRequest;
import cn.kong.zbrain.dto.response.ChatConfigResponse;
import cn.kong.zbrain.dto.response.RewriteResponse;
import cn.kong.zbrain.entity.ChatLog;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.mapper.ChatLogMapper;
import cn.kong.zbrain.mapper.ChatSessionMapper;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.service.SysLlmModelService;
import cn.kong.zbrain.service.impl.IntentRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话 Controller
 *
 * @author zbrain-team
 */
@Slf4j
@Tag(name = "对话问答")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final IntentRouter intentRouter;
    private final QueryPreprocessService queryPreprocessService;
    private final SysLlmModelService sysLlmModelService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatLogMapper chatLogMapper;

    private static final long SSE_TIMEOUT = 300_000L; // 5 分钟

    @Operation(summary = "获取对话页配置（工作模式 + 模型列表）")
    @GetMapping("/config")
    public Result<ChatConfigResponse> getChatConfig() {
        ChatConfigResponse config = new ChatConfigResponse();

        // 工作模式
        config.setModes(List.of(
                new ChatConfigResponse.WorkMode("ask", "问答模式", "基于知识库进行 RAG 问答"),
                new ChatConfigResponse.WorkMode("agent", "任务模式", "Agent 自主执行多步任务")
        ));

        // 可用模型列表（仅活跃的 chat 模型）
        List<SysLlmModel> allModels = sysLlmModelService.listByType("chat");
        Long defaultModelId = null;
        List<ChatConfigResponse.ModelOption> modelOptions = new java.util.ArrayList<>();
        for (SysLlmModel m : allModels) {
            if (m.getIsActive() == null || !m.getIsActive()) continue;
            modelOptions.add(new ChatConfigResponse.ModelOption(
                    m.getId(), m.getName(), m.getModelName(), m.getIsDefault()));
            if (Boolean.TRUE.equals(m.getIsDefault())) {
                defaultModelId = m.getId();
            }
        }
        config.setModels(modelOptions);
        config.setDefaultModelId(defaultModelId);

        return Result.success(config);
    }

    @Operation(summary = "流式问答（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(throwable -> log.error("SSE 连接异常", throwable));

        intentRouter.routeStream(request, emitter);
        return emitter;
    }

    @Operation(summary = "优化输入，增强提示词")
    @PostMapping("/rewrite")
    public Result<RewriteResponse> rewriteQuery(@Valid @RequestBody RewriteRequest request) {
        String rewritten = queryPreprocessService.rewriteQueryForApi(
                request.getQuery(),
                request.getSessionId()
        );
        return Result.success(new RewriteResponse(request.getQuery(), rewritten));
    }

    @Operation(summary = "获取最近会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        String userId = "anonymous";
        int offset = (pageNum - 1) * pageSize;
        List<ChatSession> sessions = chatSessionMapper.selectByUserId(userId, offset, pageSize);
        return Result.success(sessions);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionMapper.deleteById(sessionId);
        return Result.success();
    }

    @Operation(summary = "获取会话历史消息")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatLog>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatLog> logs = chatLogMapper.selectBySessionId(sessionId);
        return Result.success(logs);
    }
}
