package cn.kong.zbrain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.entity.PromptTemplate;
import cn.kong.zbrain.enums.ChatIntent;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.mapper.DocumentMapper;
import cn.kong.zbrain.service.ChatEngine;
import cn.kong.zbrain.service.HybridRetrievalService;
import cn.kong.zbrain.service.PromptTemplateService;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.service.SysPromptService;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库问答引擎（RAG）
 *
 * <p>完整 RAG 链路：</p>
 * <ol>
 *   <li>查询预处理：Query 改写、HyDE</li>
 *   <li>混合检索：向量/全文 + RRF 融合 + Rerank</li>
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
public class RAGEngine implements ChatEngine {

    private final QueryPreprocessService queryPreprocessService;
    private final HybridRetrievalService hybridRetrievalService;
    private final PromptTemplateService promptTemplateService;
    private final LLMService llmService;
    private final ChatContextCache chatContextCache;
    private final ChatSessionHelper helper;
    private final DocumentMapper documentMapper;
    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;
    private final SysPromptService sysPromptService;

    /** 引用标记正则：[doc_1] 或 doc_1 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[?doc_(\\d+)\\]?");

    @Override
    public ChatIntent supportedIntent() {
        return ChatIntent.RAG;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();

        ChatSession session = helper.getOrCreateSession(request);

        // 1. 查询预处理（Query 改写、HyDE）
        QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                request.getQuery(), session.getId());

        // 2. 混合检索
        List<RetrievalResult> retrievalResults = hybridRetrievalService.hybridRetrieve(
                request.getKbId(), preprocess.vectorQuery(), preprocess.textQuery());

        if (retrievalResults.isEmpty()) {
            String noResultMsg = getNoResultMessage();
            ChatResponse resp = new ChatResponse();
            resp.setSessionId(session.getId());
            resp.setQuery(request.getQuery());
            resp.setRewrittenQuery(preprocess.rewrittenQuery());
            resp.setHydeAnswer(preprocess.hydeAnswer());
            resp.setAnswer(noResultMsg);
            resp.setCitations(new ArrayList<>());
            resp.setHitChunkIds(new ArrayList<>());
            resp.setCostTimeMs(System.currentTimeMillis() - startTime);
            helper.saveLog(request, session, preprocess, resp, retrievalResults);
            return resp;
        }

        // 3. Token 预算控制 + 上下文组装
        ContextAssembly context = assembleContext(retrievalResults);

        // 4. 动态 Prompt 组装
        PromptTemplate template = getPromptTemplate(request.getKbId());
        String userPrompt = buildUserPrompt(template.getUserPrompt(), context.contextText(), request.getQuery());

        // 5. 调用 LLM 生成回答
        List<LLMService.ChatMessage> history = helper.convertHistory(preprocess.history());
        String answer = llmService.chat(request.getModelId(), template.getSystemPrompt(), userPrompt, history,
                Boolean.TRUE.equals(request.getThinking()));

        // 6. 引用溯源
        List<ChatResponse.Citation> citations = extractCitations(answer, context.citationMap());

        // 7. 组装响应
        ChatResponse response = new ChatResponse();
        response.setSessionId(session.getId());
        response.setQuery(request.getQuery());
        response.setRewrittenQuery(preprocess.rewrittenQuery());
        response.setHydeAnswer(preprocess.hydeAnswer());
        response.setAnswer(answer);
        response.setCitations(citations);
        response.setHitChunkIds(retrievalResults.stream().map(RetrievalResult::getChunkId).toList());
        response.setCostTimeMs(System.currentTimeMillis() - startTime);

        // 8. 上下文与日志沉淀
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
        chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(answer));
        helper.incrementMessageCount(session.getId());
        helper.saveLog(request, session, preprocess, response, retrievalResults);

        return response;
    }

    @Override
    public void chatStream(ChatRequest request, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        try {
            ChatSession session = helper.getOrCreateSession(request);

            // 1. 查询预处理（Query 改写、HyDE）
            QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                    request.getQuery(), session.getId());

            // 2. 混合检索
            List<RetrievalResult> retrievalResults = hybridRetrievalService.hybridRetrieve(
                    request.getKbId(), preprocess.vectorQuery(), preprocess.textQuery());

            // 3. 发送检索结果元信息
            helper.sendSseEvent(emitter, "session", session.getId());
            helper.sendSseEvent(emitter, "intent", "rag");
            helper.sendSseEvent(emitter, "rewritten_query", preprocess.rewrittenQuery());
            helper.sendSseEvent(emitter, "hyde", preprocess.hydeAnswer());
            helper.sendSseEvent(emitter, "retrieval", retrievalResults.stream()
                    .map(r -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("chunkId", r.getChunkId());
                        m.put("score", r.getScore());
                        m.put("docId", r.getDocId());
                        m.put("citationLabel", r.getCitationLabel());
                        return m;
                    })
                    .toList());

            if (retrievalResults.isEmpty()) {
                String noResultMsg = getNoResultMessage();
                helper.sendSseEvent(emitter, "content", noResultMsg);
                helper.sendSseEvent(emitter, "done", Map.of("costTimeMs", System.currentTimeMillis() - startTime));
                emitter.complete();
                return;
            }

            // 4. Token 预算控制 + 上下文组装
            ContextAssembly context = assembleContext(retrievalResults);

            // 5. 动态 Prompt 组装
            PromptTemplate template = getPromptTemplate(request.getKbId());
            String userPrompt = buildUserPrompt(template.getUserPrompt(), context.contextText(), request.getQuery());

            // 6. 发送引用信息（含文档名、内容片段与完整内容）
            helper.sendSseEvent(emitter, "citations", context.citationMap().entrySet().stream()
                    .map(e -> {
                        RetrievalResult r = e.getValue();
                        Map<String, Object> m = new HashMap<>();
                        m.put("label", "doc_" + e.getKey());
                        m.put("chunkId", r.getChunkId());
                        m.put("docId", r.getDocId());
                        Document doc = documentMapper.selectById(r.getDocId());
                        if (doc != null) {
                            m.put("docName", doc.getFileName());
                        }
                        String childContent = r.getContent();
                        if (childContent != null && childContent.length() > 300) {
                            childContent = childContent.substring(0, 300) + "...";
                        }
                        m.put("snippet", childContent != null ? childContent : "");
                        String fullContent = r.getParentContent() != null
                                ? r.getParentContent() : r.getContent();
                        m.put("fullContent", fullContent != null ? fullContent : "");
                        if (r.getMetadata() != null) {
                            try {
                                Map<?, ?> meta = objectMapper.readValue(r.getMetadata(), Map.class);
                                Object page = meta.get("page");
                                if (page instanceof Number n) {
                                    m.put("page", n.intValue());
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        return m;
                    })
                    .toList());

            // 7. 流式生成
            StringBuilder fullAnswer = new StringBuilder();
            llmService.chatStream(
                    request.getModelId(),
                    template.getSystemPrompt(),
                    userPrompt,
                    helper.convertHistory(preprocess.history()),
                    Boolean.TRUE.equals(request.getThinking()),
                    chunk -> {
                        fullAnswer.append(chunk);
                        helper.sendSseEvent(emitter, "content", chunk);
                    });

            // 8. 上下文与日志沉淀
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(fullAnswer.toString()));
            helper.incrementMessageCount(session.getId());

            // 保存日志到数据库（供历史消息加载）
            ChatResponse logResponse = new ChatResponse();
            logResponse.setSessionId(session.getId());
            logResponse.setQuery(request.getQuery());
            logResponse.setRewrittenQuery(preprocess.rewrittenQuery());
            logResponse.setHydeAnswer(preprocess.hydeAnswer());
            logResponse.setAnswer(fullAnswer.toString());
            logResponse.setCitations(new ArrayList<>());
            logResponse.setHitChunkIds(retrievalResults.stream().map(RetrievalResult::getChunkId).toList());
            logResponse.setCostTimeMs(System.currentTimeMillis() - startTime);
            helper.saveLog(request, session, preprocess, logResponse, retrievalResults);

            // 9. 发送完成事件
            helper.sendSseEvent(emitter, "done", Map.of("costTimeMs", System.currentTimeMillis() - startTime));
            emitter.complete();

        } catch (Exception e) {
            log.error("RAG 引擎流式失败", e);
            helper.sendSseEvent(emitter, "error", e.getMessage());
            emitter.complete();
        }
    }

    // ==================== 内部方法 ====================

    private String getNoResultMessage() {
        String noResultMsg = sysPromptService.getContent("no_result");
        if (noResultMsg == null) {
            noResultMsg = "抱歉，知识库中未找到与您问题相关的内容，请尝试更换问法或联系管理员补充知识。";
        }
        return noResultMsg;
    }

    /**
     * 获取提示词模板：kbId 不为空时优先知识库专属模板，否则使用默认模板
     */
    private PromptTemplate getPromptTemplate(Long kbId) {
        if (kbId != null) {
            return promptTemplateService.getByKbId(kbId);
        }
        return promptTemplateService.getDefault();
    }

    /**
     * Token 预算控制 + 上下文组装
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
                Document doc = documentMapper.selectById(result.getDocId());
                if (doc != null) {
                    citation.setDocName(doc.getFileName());
                }
                citation.setSnippet(result.getContent());
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

    /**
     * 上下文组装结果
     */
    private record ContextAssembly(String contextText, Map<Integer, RetrievalResult> citationMap) {}
}
