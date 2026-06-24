package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答服务接口
 *
 * <p>核心 RAG 链路：查询预处理 -> 混合检索 -> 重排序 -> Token 预算控制 -> 问答生成 -> 引用溯源。</p>
 *
 * @author zbrain-team
 */
public interface ChatService {

    /**
     * 同步问答
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式问答（SSE）
     */
    void chatStream(ChatRequest request, SseEmitter emitter);

    /**
     * 闲聊（不经过 RAG 检索）
     */
    ChatResponse chitchat(ChatRequest request);
}
