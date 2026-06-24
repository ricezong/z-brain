package cn.kong.zbrain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.ChatLog;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.entity.PromptTemplate;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.mapper.ChatLogMapper;
import cn.kong.zbrain.mapper.ChatSessionMapper;
import cn.kong.zbrain.mapper.DocumentMapper;
import cn.kong.zbrain.service.ChatService;
import cn.kong.zbrain.service.HybridRetrievalService;
import cn.kong.zbrain.service.PromptTemplateService;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.util.CommonUtils;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问答服务实现（核心 RAG 链路）
 *
 * <p>完整链路：</p>
 * <ol>
 *   <li>查询预处理：意图识别、Query 改写、HyDE</li>
 *   <li>混合检索：向量/全文/模糊 + RRF 融合 + Rerank</li>
 *   <li>Token 预算控制：提取 Top 5 子块对应父块，按预算截断</li>
 *   <li>问答生成：动态 Prompt 组装，强制引用标记</li>
 *   <li>引用溯源：解析 doc_x 标记，映射为前端可点击链接</li>
 *   <li>上下文与日志沉淀：Redis 短期 + PG 长期</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final QueryPreprocessService queryPreprocessService;
    private final HybridRetrievalService hybridRetrievalService;
    private final PromptTemplateService promptTemplateService;
    private final LLMService llmService;
    private final ChatContextCache chatContextCache;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatLogMapper chatLogMapper;
    private final DocumentMapper documentMapper;
    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;

    /** 引用标记正则：[doc_1] 或 doc_1 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[?doc_(\\d+)\\]?");

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. 创建或获取会话
        ChatSession session = getOrCreateSession(request);

        // 2. 查询预处理
        QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                request.getQuery(), session.getId(),
                Boolean.TRUE.equals(request.getEnableQueryRewrite()),
                Boolean.TRUE.equals(request.getEnableHyde()));

        // 3. 闲聊直接路由至 LLM
        if (preprocess.isChitchat()) {
            return chitchatInternal(request, session, preprocess, startTime);
        }

        // 4. 混合检索
        List<RetrievalResult> retrievalResults = hybridRetrievalService.hybridRetrieve(
                request.getKbId(), preprocess.vectorQuery(), preprocess.textQuery());

        if (retrievalResults.isEmpty()) {
            ChatResponse resp = new ChatResponse();
            resp.setSessionId(session.getId());
            resp.setQuery(request.getQuery());
            resp.setRewrittenQuery(preprocess.rewrittenQuery());
            resp.setHydeAnswer(preprocess.hydeAnswer());
            resp.setAnswer("抱歉，知识库中未找到与您问题相关的内容，请尝试更换问法或联系管理员补充知识。");
            resp.setCitations(new ArrayList<>());
            resp.setHitChunkIds(new ArrayList<>());
            resp.setCostTimeMs(System.currentTimeMillis() - startTime);
            saveLog(request, session, preprocess, resp, retrievalResults);
            return resp;
        }

        // 5. Token 预算控制 + 上下文组装
        ContextAssembly context = assembleContext(retrievalResults);

        // 6. 动态 Prompt 组装
        PromptTemplate template = promptTemplateService.getByKbId(request.getKbId());
        String userPrompt = buildUserPrompt(template.getUserPrompt(), context.contextText(), request.getQuery());

        // 7. 调用 LLM 生成回答
        List<LLMService.ChatMessage> history = convertHistory(preprocess.history());
        String answer = llmService.chat(template.getSystemPrompt(), userPrompt, history);

        // 8. 引用溯源
        List<ChatResponse.Citation> citations = extractCitations(answer, context.citationMap());

        // 9. 组装响应
        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setQuery(request.getQuery());
        response.setRewrittenQuery(preprocess.rewrittenQuery());
        response.setHydeAnswer(preprocess.hydeAnswer());
        response.setAnswer(answer);
        response.setCitations(citations);
        response.setHitChunkIds(retrievalResults.stream().map(RetrievalResult::getChunkId).toList());
        response.setCostTimeMs(System.currentTimeMillis() - startTime);

        // 10. 上下文与日志沉淀
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(answer));
        chatSessionMapper.incrementMessageCount(session.getId());
        saveLog(request, session, preprocess, response, retrievalResults);

        return response;
    }

    @Override
    public void chatStream(ChatRequest request, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 创建或获取会话
            ChatSession session = getOrCreateSession(request);

            // 2. 查询预处理
            QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                    request.getQuery(), session.getId(),
                    Boolean.TRUE.equals(request.getEnableQueryRewrite()),
                    Boolean.TRUE.equals(request.getEnableHyde()));

            // 3. 闲聊
            if (preprocess.isChitchat()) {
                sendSseEvent(emitter, "session", session.getId());
                sendSseEvent(emitter, "rewritten_query", preprocess.rewrittenQuery());
                StringBuilder fullAnswer = new StringBuilder();
                llmService.chatStream(
                        "你是智多星知识库助手，请友好地回答用户问题。",
                        request.getQuery(),
                        convertHistory(preprocess.history()),
                        chunk -> {
                            fullAnswer.append(chunk);
                            sendSseEvent(emitter, "content", chunk);
                        });
                sendSseEvent(emitter, "done", "complete");
                emitter.complete();
                return;
            }

            // 4. 混合检索
            List<RetrievalResult> retrievalResults = hybridRetrievalService.hybridRetrieve(
                    request.getKbId(), preprocess.vectorQuery(), preprocess.textQuery());

            // 5. 发送检索结果元信息
            sendSseEvent(emitter, "session", session.getId());
            sendSseEvent(emitter, "rewritten_query", preprocess.rewrittenQuery());
            sendSseEvent(emitter, "hyde", preprocess.hydeAnswer());
            sendSseEvent(emitter, "retrieval", retrievalResults.stream()
                    .map(r -> Map.of("chunkId", r.getChunkId(), "score", r.getScore(),
                            "docId", r.getDocId(), "citationLabel", r.getCitationLabel()))
                    .toList());

            if (retrievalResults.isEmpty()) {
                sendSseEvent(emitter, "content", "抱歉，知识库中未找到与您问题相关的内容。");
                sendSseEvent(emitter, "done", "complete");
                emitter.complete();
                return;
            }

            // 6. Token 预算控制 + 上下文组装
            ContextAssembly context = assembleContext(retrievalResults);

            // 7. 动态 Prompt 组装
            PromptTemplate template = promptTemplateService.getByKbId(request.getKbId());
            String userPrompt = buildUserPrompt(template.getUserPrompt(), context.contextText(), request.getQuery());

            // 8. 流式生成
            sendSseEvent(emitter, "citations", context.citationMap().entrySet().stream()
                    .map(e -> Map.of("label", "doc_" + e.getKey(),
                            "chunkId", e.getValue().getChunkId(),
                            "docId", e.getValue().getDocId()))
                    .toList());

            StringBuilder fullAnswer = new StringBuilder();
            llmService.chatStream(
                    template.getSystemPrompt(),
                    userPrompt,
                    convertHistory(preprocess.history()),
                    chunk -> {
                        fullAnswer.append(chunk);
                        sendSseEvent(emitter, "content", chunk);
                    });

            // 9. 上下文与日志沉淀
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(fullAnswer.toString()));
            chatSessionMapper.incrementMessageCount(session.getId());

            // 10. 发送完成事件
            sendSseEvent(emitter, "done", Map.of("costTimeMs", System.currentTimeMillis() - startTime));
            emitter.complete();

        } catch (Exception e) {
            log.error("流式问答失败", e);

            sendSseEvent(emitter, "error", e.getMessage());
            emitter.complete();

        }
    }

    @Override
    public ChatResponse chitchat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        ChatSession session = getOrCreateSession(request);
        QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                request.getQuery(), session.getId(), false, false);
        return chitchatInternal(request, session, preprocess, startTime);
    }

    // ==================== 内部方法 ====================

    private ChatResponse chitchatInternal(ChatRequest request, ChatSession session,
                                          QueryPreprocessService.PreprocessResult preprocess,
                                          long startTime) {
        List<LLMService.ChatMessage> history = convertHistory(preprocess.history());
        String answer = llmService.chat(
                "你是智多星知识库助手，请友好地回答用户问题。",
                request.getQuery(),
                history);

        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setQuery(request.getQuery());
        response.setRewrittenQuery(preprocess.rewrittenQuery());
        response.setAnswer(answer);
        response.setCitations(new ArrayList<>());
        response.setHitChunkIds(new ArrayList<>());
        response.setCostTimeMs(System.currentTimeMillis() - startTime);

        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(answer));
        chatSessionMapper.incrementMessageCount(session.getId());
        saveLog(request, session, preprocess, response, new ArrayList<>());
        return response;
    }

    private ChatSession getOrCreateSession(ChatRequest request) {
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
     * Token 预算控制 + 上下文组装
     *
     * <p>提取 Top 5 子块对应的父块内容，按精排分值从高到低填入，
     * 预留 2K Token 给系统提示词、历史对话和用户问题，剩余 6K 预算用于父块内容。</p>
     */
    private ContextAssembly assembleContext(List<RetrievalResult> retrievalResults) {
        int budget = properties.getLlm().getContextTokenBudget() - properties.getLlm().getReserveToken();
        StringBuilder contextText = new StringBuilder();
        Map<Integer, RetrievalResult> citationMap = new HashMap<>();
        int usedTokens = 0;
        int citationIndex = 1;

        for (RetrievalResult result : retrievalResults) {
            String content = result.getParentContent() != null
                    ? result.getParentContent()
                    : result.getContent();
            int tokens = TokenUtils.countTokens(content);
            if (usedTokens + tokens > budget) {
                // 截断
                int remaining = budget - usedTokens;
                if (remaining > 100) {
                    content = content.substring(0, Math.min(content.length(), remaining * 2));
                    tokens = TokenUtils.countTokens(content);
                    contextText.append("[doc_").append(citationIndex).append("]\n")
                            .append(content).append("\n\n");
                    result.setCitationLabel("doc_" + citationIndex);
                    citationMap.put(citationIndex, result);
                    usedTokens += tokens;
                }
                break;
            }
            contextText.append("[doc_").append(citationIndex).append("]\n")
                    .append(content).append("\n\n");
            result.setCitationLabel("doc_" + citationIndex);
            citationMap.put(citationIndex, result);
            usedTokens += tokens;
            citationIndex++;
        }

        return new ContextAssembly(contextText.toString(), citationMap);
    }

    private String buildUserPrompt(String template, String context, String question) {
        return template
                .replace("{context}", context)
                .replace("{question}", question);
    }

    /**
     * 解析引用标记并映射为前端可点击的引用
     */
    private List<ChatResponse.Citation> extractCitations(String answer,
                                                          Map<Integer, RetrievalResult> citationMap) {
        List<ChatResponse.Citation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            RetrievalResult result = citationMap.get(index);
            if (result != null) {
                ChatResponse.Citation citation = new ChatResponse.Citation();
                citation.setLabel("doc_" + index);
                citation.setChunkId(result.getChunkId());
                citation.setDocId(result.getDocId());
                // 查询文档名称
                Document doc = documentMapper.selectById(result.getDocId());
                if (doc != null) {
                    citation.setDocName(doc.getFileName());
                }
                citation.setSnippet(result.getContent());
                // 从 metadata 提取页码
                if (result.getMetadata() != null) {
                    try {
                        Map<?, ?> meta = objectMapper.readValue(result.getMetadata(), Map.class);
                        Object page = meta.get("page");
                        if (page instanceof Number n) {
                            citation.setPage(n.intValue());
                        }
                    } catch (Exception ignored) {
                    }
                }
                citations.add(citation);
            }
        }
        return citations;
    }

    private List<LLMService.ChatMessage> convertHistory(List<ChatContextCache.ChatMessage> history) {
        if (history == null) {
            return new ArrayList<>();
        }
        return history.stream()
                .map(m -> new LLMService.ChatMessage(m.role(), m.content()))
                .toList();
    }

    private void saveLog(ChatRequest request, ChatSession session,
                         QueryPreprocessService.PreprocessResult preprocess,
                         ChatResponse response,
                         List<RetrievalResult> retrievalResults) {
        try {
            ChatLog log = new ChatLog();
            log.setSessionId(session.getId());
            log.setKbId(request.getKbId());
            log.setUserId(request.getUserId());
            log.setQuery(request.getQuery());
            log.setRewrittenQuery(preprocess.rewrittenQuery());
            log.setHydeAnswer(preprocess.hydeAnswer());
            log.setAnswer(response.getAnswer());
            log.setHitChunkIds(objectMapper.writeValueAsString(response.getHitChunkIds()));

            Map<String, Object> retrievalInfo = new HashMap<>();
            retrievalInfo.put("hitCount", retrievalResults.size());
            retrievalInfo.put("chunkIds", retrievalResults.stream()
                    .map(RetrievalResult::getChunkId).toList());
            log.setRetrievalInfo(objectMapper.writeValueAsString(retrievalInfo));

            Map<String, Object> tokenUsage = new HashMap<>();
            tokenUsage.put("costTimeMs", response.getCostTimeMs());
            log.setTokenUsage(objectMapper.writeValueAsString(tokenUsage));
            log.setCostTimeMs(response.getCostTimeMs());

            chatLogMapper.insert(log);
        } catch (Exception e) {
            log.error("保存对话日志失败", e);
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
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
     * 上下文组装结果
     */
    private record ContextAssembly(String contextText, Map<Integer, RetrievalResult> citationMap) {}
}
