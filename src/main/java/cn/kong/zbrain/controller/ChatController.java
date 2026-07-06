package cn.kong.zbrain.controller;

import cn.kong.zbrain.common.Result;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.request.RewriteRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.RewriteResponse;
import cn.kong.zbrain.service.ChatService;
import cn.kong.zbrain.service.QueryPreprocessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private final ChatService chatService;
    private final QueryPreprocessService queryPreprocessService;

    private static final long SSE_TIMEOUT = 300_000L; // 5 分钟

    /*@Operation(summary = "同步问答")
    @PostMapping("/sync")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        request.setStream(false);
        return Result.success(chatService.chat(request));
    }*/

    @Operation(summary = "流式问答（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        request.setStream(true);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(throwable -> log.error("SSE 连接异常", throwable));

        chatService.chatStream(request, emitter);
        return emitter;
    }

    @Operation(summary = "优化输入，增强提示词")
    @PostMapping("/rewrite")
    public Result<RewriteResponse> rewriteQuery(@Valid @RequestBody RewriteRequest request) {
        String rewritten = queryPreprocessService.rewriteQuery(
                request.getQuery(),
                request.getSessionId()
        );
        return Result.success(new RewriteResponse(request.getQuery(), rewritten));
    }
}
