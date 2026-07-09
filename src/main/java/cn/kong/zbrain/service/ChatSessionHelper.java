package cn.kong.zbrain.service;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.ChatLog;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.mapper.ChatLogMapper;
import cn.kong.zbrain.mapper.ChatSessionMapper;
import cn.kong.zbrain.util.CommonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话公共辅助类
 *
 * <p>提取所有引擎共用的逻辑：会话管理、SSE 事件发送、历史对话转换、日志保存。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionHelper {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatLogMapper chatLogMapper;
    private final ObjectMapper objectMapper;
    private final SysLlmModelService sysLlmModelService;

    /**
     * 创建或获取会话
     */
    public ChatSession getOrCreateSession(ChatRequest request) {
        if (CommonUtils.isNotBlank(request.getSessionId())) {
            ChatSession existing = chatSessionMapper.selectById(request.getSessionId());
            if (existing != null) {
                return existing;
            }
        }
        ChatSession session = new ChatSession();
        session.setId(CommonUtils.uuid());
        session.setKbId(request.getKbId());
        session.setTitle(request.getQuery().length() > 50
                ? request.getQuery().substring(0, 50) + "..."
                : request.getQuery());
        session.setUserId(CommonUtils.isNotBlank(request.getUserId()) ? request.getUserId() : "anonymous");
        session.setMessageCount(0);
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 发送 SSE 事件
     */
    public void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        if (data == null) {
            data = "";
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.error("发送 SSE 事件失败", e);
        } catch (IllegalStateException e) {
            log.warn("SSE 连接已关闭，跳过事件: {}", eventName);
        }
    }

    /**
     * 将缓存中的历史消息转换为 LLM 消息格式
     */
    public List<LLMService.ChatMessage> convertHistory(List<ChatContextCache.ChatMessage> history) {
        if (history == null) {
            return new ArrayList<>();
        }
        return history.stream()
                .map(m -> new LLMService.ChatMessage(m.role(), m.content()))
                .toList();
    }

    /**
     * 递增会话消息计数
     */
    public void incrementMessageCount(String sessionId) {
        chatSessionMapper.incrementMessageCount(sessionId);
    }

    /**
     * 分页查询用户会话列表
     *
     * @param userId   用户 ID
     * @param offset   偏移量
     * @param pageSize 每页大小
     * @return 会话列表
     */
    public List<ChatSession> listSessions(String userId, int offset, int pageSize) {
        return chatSessionMapper.selectByUserId(userId, offset, pageSize);
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    public void deleteSession(String sessionId) {
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 获取会话历史消息
     *
     * @param sessionId 会话 ID
     * @return 对话日志列表
     */
    public List<ChatLog> getSessionMessages(String sessionId) {
        return chatLogMapper.selectBySessionId(sessionId);
    }

    /**
     * 保存对话日志
     */
    public void saveLog(ChatRequest request, ChatSession session,
                        QueryPreprocessService.PreprocessResult preprocess,
                        ChatResponse response,
                        List<RetrievalResult> retrievalResults) {
        try {
            ChatLog logEntry = new ChatLog();
            logEntry.setSessionId(session.getId());
            logEntry.setKbId(request.getKbId());
            logEntry.setUserId(request.getUserId());
            logEntry.setQuery(request.getQuery());
            logEntry.setRewrittenQuery(preprocess != null ? preprocess.rewrittenQuery() : null);
            logEntry.setAnswer(response.getAnswer());
            logEntry.setHitChunkIds(objectMapper.writeValueAsString(response.getHitChunkIds() != null
                    ? response.getHitChunkIds() : new ArrayList<>()));

            Map<String, Object> retrievalInfo = new HashMap<>();
            retrievalInfo.put("hitCount", retrievalResults != null ? retrievalResults.size() : 0);
            retrievalInfo.put("chunkIds", retrievalResults != null
                    ? retrievalResults.stream().map(RetrievalResult::getChunkId).toList()
                    : new ArrayList<>());
            logEntry.setRetrievalInfo(objectMapper.writeValueAsString(retrievalInfo));

            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("costTimeMs", response.getCostTimeMs());
            metaMap.put("intent", response.getIntent());
            // 解析模型显示名称
            String modelName = response.getModelName();
            if (modelName == null && request.getModelId() != null) {
                SysLlmModel model = sysLlmModelService.getById(request.getModelId());
                if (model != null) {
                    modelName = model.getName();
                }
            }
            metaMap.put("modelName", modelName);
            if (response.getTokenMeta() != null) {
                metaMap.put("promptTokens", response.getTokenMeta().getPromptTokens());
                metaMap.put("completionTokens", response.getTokenMeta().getCompletionTokens());
                metaMap.put("totalTokens", response.getTokenMeta().getTotalTokens());
            }
            logEntry.setMeta(objectMapper.writeValueAsString(metaMap));
            logEntry.setCostTimeMs(response.getCostTimeMs());

            chatLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("保存对话日志失败", e);
        }
    }
}
