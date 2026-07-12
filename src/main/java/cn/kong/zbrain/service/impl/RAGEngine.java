package cn.kong.zbrain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.request.ChatRequest;
import cn.kong.zbrain.dto.response.ChatResponse;
import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.dto.response.ThinkingStep;
import cn.kong.zbrain.entity.ChatSession;
import cn.kong.zbrain.entity.Document;
import cn.kong.zbrain.entity.PromptTemplate;
import cn.kong.zbrain.enums.ChatIntent;
import cn.kong.zbrain.enums.PromptKey;
import cn.kong.zbrain.enums.SseEventType;
import cn.kong.zbrain.enums.ThinkingStepType;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.mapper.DocumentMapper;
import cn.kong.zbrain.service.ChatEngine;
import cn.kong.zbrain.service.ChatSessionHelper;
import cn.kong.zbrain.service.HybridRetrievalService;
import cn.kong.zbrain.service.PromptTemplateService;
import cn.kong.zbrain.service.QueryPreprocessService;
import cn.kong.zbrain.service.SysPromptService;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识库问答引擎（RAG）
 *
 * <p>完整 RAG 链路：</p>
 * <ol>
 *   <li>查询预处理：Query 改写</li>
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

        // 1. 查询预处理（Query 改写）
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
        response.setAnswer(answer);
        response.setCitations(citations);
        response.setHitChunkIds(retrievalResults.stream().map(RetrievalResult::getChunkId).toList());
        response.setCostTimeMs(System.currentTimeMillis() - startTime);
        response.setIntent("rag");

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

            // 思考过程回调
            Consumer<ThinkingStep> onThinking = step -> helper.sendSseEvent(emitter, SseEventType.THINKING.getCode(), step);

            // 1. 查询预处理（Query 改写）
            QueryPreprocessService.PreprocessResult preprocess = queryPreprocessService.preprocess(
                    request.getQuery(), session.getId(), onThinking);

            // 2. 混合检索
            List<RetrievalResult> retrievalResults = hybridRetrievalService.hybridRetrieve(
                    request.getKbId(), preprocess.vectorQuery(), preprocess.textQuery(), onThinking);

            // 3. 发送检索结果元信息
            helper.sendSseEvent(emitter, SseEventType.SESSION.getCode(), session.getId());
            helper.sendSseEvent(emitter, SseEventType.INTENT.getCode(), "rag");

            if (retrievalResults.isEmpty()) {
                String noResultMsg = getNoResultMessage();
                helper.sendSseEvent(emitter, SseEventType.CONTENT.getCode(), noResultMsg);
                helper.sendSseEvent(emitter, SseEventType.DONE.getCode(), Map.of("costTimeMs", System.currentTimeMillis() - startTime));
                emitter.complete();
                return;
            }

            // 4. Token 预算控制 + 上下文组装
            ContextAssembly context = assembleContext(retrievalResults);

            // 5. 动态 Prompt 组装
            PromptTemplate template = getPromptTemplate(request.getKbId());
            String userPrompt = buildUserPrompt(template.getUserPrompt(), context.contextText(), request.getQuery());

            // 6. 构建引用列表（SSE 发送 + 日志持久化共用）
            List<ChatResponse.Citation> citationList = buildCitationList(context.citationMap());

            // 发送引用信息（含文档名、内容片段与完整内容）
            helper.sendSseEvent(emitter, SseEventType.CITATIONS.getCode(), citationList);

            // 7. 发送生成开始思考步骤
            onThinking.accept(new ThinkingStep(
                    ThinkingStepType.GENERATION.getCode(), "生成回答",
                    "模型: " + (request.getModelId() != null ? request.getModelId() : "默认模型"),
                    System.currentTimeMillis()));

            // 8. 流式生成
            StringBuilder fullAnswer = new StringBuilder();
            AtomicReference<Usage> usageRef = new AtomicReference<>();
            llmService.chatStream(
                    request.getModelId(),
                    template.getSystemPrompt(),
                    userPrompt,
                    helper.convertHistory(preprocess.history()),
                    Boolean.TRUE.equals(request.getThinking()),
                    chunk -> {
                        fullAnswer.append(chunk);
                        helper.sendSseEvent(emitter, SseEventType.CONTENT.getCode(), chunk);
                    },
                    usage -> usageRef.set(usage)
            );

            // 9. 上下文与日志沉淀
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.user(request.getQuery()));
            chatContextCache.appendMessage(session.getId(), ChatContextCache.ChatMessage.assistant(fullAnswer.toString()));
            helper.incrementMessageCount(session.getId());

            // 保存日志到数据库（供历史消息加载）
            ChatResponse logResponse = new ChatResponse();
            logResponse.setSessionId(session.getId());
            logResponse.setQuery(request.getQuery());
            logResponse.setRewrittenQuery(preprocess.rewrittenQuery());
            logResponse.setAnswer(fullAnswer.toString());
            logResponse.setCitations(citationList);
            logResponse.setHitChunkIds(retrievalResults.stream().map(RetrievalResult::getChunkId).toList());
            logResponse.setCostTimeMs(System.currentTimeMillis() - startTime);
            logResponse.setIntent("rag");
            Usage usage = usageRef.get();
            if (usage != null) {
                ChatResponse.TokenMeta tu = new ChatResponse.TokenMeta();
                tu.setPromptTokens(usage.getPromptTokens());
                tu.setCompletionTokens(usage.getCompletionTokens());
                tu.setTotalTokens(usage.getTotalTokens());
                logResponse.setTokenMeta(tu);
            }
            helper.saveLog(request, session, preprocess, logResponse, retrievalResults);

            // 10. 发送完成事件
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("costTimeMs", System.currentTimeMillis() - startTime);
            if (usage != null) {
                doneData.put("promptTokens", usage.getPromptTokens());
                doneData.put("completionTokens", usage.getCompletionTokens());
                doneData.put("totalTokens", usage.getTotalTokens());
            }
            helper.sendSseEvent(emitter, SseEventType.DONE.getCode(), doneData);
            emitter.complete();

        } catch (Exception e) {
            log.error("RAG 引擎流式失败", e);
            helper.sendSseEvent(emitter, SseEventType.ERROR.getCode(), e.getMessage());
            emitter.complete();
        }
    }

    // ==================== 内部方法 ====================

    private String getNoResultMessage() {
        String noResultMsg = sysPromptService.getContent(PromptKey.NO_RESULT.getCode());
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
     * 解析引用标记并映射为前端可点击的引用（仅返回回答中实际引用的）
     */
    private List<ChatResponse.Citation> extractCitations(String answer,
                                                          Map<Integer, RetrievalResult> citationMap) {
        List<ChatResponse.Citation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            RetrievalResult result = citationMap.get(index);
            if (result != null) {
                citations.add(buildSingleCitation(index, result));
            }
        }
        return citations;
    }

    /**
     * 根据检索结果构建完整引用列表（用于 SSE 推送和日志持久化）
     */
    private List<ChatResponse.Citation> buildCitationList(Map<Integer, RetrievalResult> citationMap) {
        List<ChatResponse.Citation> citations = new ArrayList<>();
        citationMap.forEach((index, result) -> citations.add(buildSingleCitation(index, result)));
        return citations;
    }

    /**
     * 构建单个引用对象
     */
    private ChatResponse.Citation buildSingleCitation(int index, RetrievalResult result) {
        ChatResponse.Citation citation = new ChatResponse.Citation();
        citation.setLabel("doc_" + index);
        citation.setChunkId(result.getChunkId());
        citation.setDocId(result.getDocId());
        Document doc = documentMapper.selectById(result.getDocId());
        if (doc != null) {
            citation.setDocName(doc.getFileName());
        }
        // snippet：子块内容摘要（截断至 300 字符）
        String childContent = result.getContent();
        if (childContent != null && childContent.length() > 300) {
            childContent = childContent.substring(0, 300) + "...";
        }
        citation.setSnippet(childContent != null ? childContent : "");
        // fullContent：父块完整内容（用于弹窗展示）
        String fullContent = result.getParentContent() != null
                ? result.getParentContent() : result.getContent();
        citation.setFullContent(fullContent != null ? fullContent : "");
        // page：从元数据中解析页码
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
        return citation;
    }

    /**
     * 上下文组装结果
     */
    private record ContextAssembly(String contextText, Map<Integer, RetrievalResult> citationMap) {}
}
