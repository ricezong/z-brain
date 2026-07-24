package cn.kong.eon.agent.advisor;

import cn.kong.eon.agent.context.ArtifactStore;
import cn.kong.eon.agent.context.CompressionCache;
import cn.kong.eon.agent.context.CompressionTier;
import cn.kong.eon.agent.context.TokenBudget;
import cn.kong.eon.agent.memory.PgChatMemoryRepository;
import cn.kong.eon.config.EonProperties;
import cn.kong.eon.persistence.entity.AgentContextSummary;
import cn.kong.eon.config.PromptKey;
import cn.kong.eon.llm.ChatClientFactory;
import cn.kong.eon.persistence.mapper.AgentContextSummaryMapper;
import cn.kong.eon.persistence.mapper.ChatMessageMapper;
import cn.kong.eon.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 四级水位线压缩 Advisor（N1 上下文核心）。
 *
 * <p>在 {@code MessageChatMemoryAdvisor}(order=DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER) 之后
 * 执行（order=200），对已加载的对话历史做压缩改写，再发给模型。</p>
 *
 * <p>水位线（{@code eon.agent.context}）：</p>
 * <ul>
 *   <li>&lt;60%：不动</li>
 *   <li>60～80% SNIP：截短保护区外老消息文本（首尾各留 1500 字符），零 LLM 成本</li>
 *   <li>80～95% PRUNE：老工具结果 → {@link ArtifactStore} 落盘 + artifact:// 占位符；
 *       而 assistant 文本只留前两句</li>
 *   <li>&ge;95% COMPACT：lightClient 增量摘要（上次摘要 + delta 合并），固定结构；
 *       持久化到 agent_context_summary；摘要 + 最近 3 轮原文回填</li>
 * </ul>
 *
 * <p><b>三处对齐原方案的实现（非简化版）：</b></p>
 * <ol>
 *   <li><b>写回 ChatMemory</b>：SNIP/PRUNE 用 {@link ChatMessageMapper#updateContentById}
 *       定向更新（保留 id/msg_seq 稳定）；COMPACT 用 {@link ChatMemoryRepository#saveAll}
 *       replace（摘要 + 最近 3 轮）。压缩结果持久化，下轮加载即压缩后状态。</li>
 *   <li><b>msg_seq 决策缓存</b>：msg_seq = chat_message.id（deserialize 时注入 metadata）；
 *       决策按 (sessionId, msgSeq) 存 Redis，单调推进（已 SNIP 的只升 PRUNE/COMPACT，
 *       绝不还原），保 Prompt Cache 前缀稳定（避坑 #4）。</li>
 *   <li><b>真实 usage 触发</b>：after 存 promptTokens + totalTokens 到 Redis；
 *       before 用 JTokkit 估算，并与上轮真实 promptTokens 取较大值（保守触发，避免低估）。</li>
 * </ol>
 *
 * <p>保护区：最近 {@code protect-tail-tokens}(默认 8000) 或最近 3 轮（取更靠前）。
 * 首条 user 消息、所有 system 消息。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class TieredCompressionAdvisor implements BaseAdvisor {

    public static final int ORDER = 200;

    private static final int SNIP_KEEP_HEAD_TAIL = 1500;
    private static final int MIN_MESSAGES_TO_COMPRESS = 6;
    private static final int PROTECT_ROUNDS = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TokenBudget tokenBudget;
    private final CompressionCache compressionCache;
    private final ArtifactStore artifactStore;
    private final AgentContextSummaryMapper summaryMapper;
    private final ConfigService configService;
    private final EonProperties properties;
    private final ChatClientFactory chatClientFactory;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemoryRepository chatMemoryRepository;

    public TieredCompressionAdvisor(TokenBudget tokenBudget,
                                    CompressionCache compressionCache,
                                    ArtifactStore artifactStore,
                                    AgentContextSummaryMapper summaryMapper,
                                    ConfigService configService,
                                    EonProperties properties,
                                    @Lazy ChatClientFactory chatClientFactory,
                                    ChatMessageMapper chatMessageMapper,
                                    ChatMemoryRepository chatMemoryRepository) {
        this.tokenBudget = tokenBudget;
        this.compressionCache = compressionCache;
        this.artifactStore = artifactStore;
        this.summaryMapper = summaryMapper;
        this.configService = configService;
        this.properties = properties;
        this.chatClientFactory = chatClientFactory;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String sessionId = extractSessionId(request.context());
        if (sessionId == null) {
            return request;
        }

        List<Message> messages = request.prompt().getInstructions();
        if (messages.size() < MIN_MESSAGES_TO_COMPRESS) {
            return request;
        }

        EonProperties.Context ctx = properties.getAgent().getContext();
        int estimated = tokenBudget.estimateMessages(messages);
        // 校准：与上轮真实 promptTokens 取较大值（保守触发，方案铁律 #3）
        int[] lastUsage = compressionCache.getUsage(sessionId);
        int refTokens = estimated;
        if (lastUsage != null && lastUsage[0] > refTokens) {
            refTokens = lastUsage[0];
        }
        double ratio = (double) refTokens / ctx.getWindowTokens();

        CompressionTier targetTier;
        if (ratio >= ctx.getCompactLine()) {
            targetTier = CompressionTier.COMPACT;
            log.info("[Compression] COMPACT: session={}, tokens={}/{} (ratio={})",
                    sessionId, refTokens, ctx.getWindowTokens(), ratio);
        } else if (ratio >= ctx.getPruneLine()) {
            targetTier = CompressionTier.PRUNE;
            log.info("[Compression] PRUNE: session={}, tokens={}/{} (ratio={})",
                    sessionId, refTokens, ctx.getWindowTokens(), ratio);
        } else if (ratio >= ctx.getSnipLine()) {
            targetTier = CompressionTier.SNIP;
            log.info("[Compression] SNIP: session={}, tokens={}/{} (ratio={})",
                    sessionId, refTokens, ctx.getWindowTokens(), ratio);
        } else {
            return request;
        }

        List<Message> processed = compress(messages, sessionId, targetTier, ctx);
        if (processed == messages) {
            return request; // 无变化（引用相等）
        }
        log.info("[Compression] 完成: {} -> {} 条", messages.size(), processed.size());
        return request.mutate()
                .prompt(request.prompt().mutate().messages(processed).build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        String sessionId = extractSessionId(response.context());
        if (sessionId == null || response.chatResponse() == null) {
            return response;
        }
        var metadata = response.chatResponse().getMetadata();
        if (metadata == null || metadata.getUsage() == null) {
            return response;
        }
        var usage = metadata.getUsage();
        Integer promptTokens = usage.getPromptTokens();
        Integer totalTokens = usage.getTotalTokens();
        if (totalTokens != null) {
            // 真实 usage 持久化，供下轮 before 校准（方案铁律 #3）
            compressionCache.saveUsage(sessionId,
                    promptTokens != null ? promptTokens : 0,
                    totalTokens);
        }
        return response;
    }

    // ==================== 压缩分发 ====================

    private List<Message> compress(List<Message> messages, String sessionId,
                                   CompressionTier targetTier, EonProperties.Context ctx) {
        if (targetTier == CompressionTier.COMPACT) {
            return compact(messages, sessionId, ctx);
        }
        return snipOrPrune(messages, sessionId, targetTier, ctx);
    }

    // ==================== SNIP / PRUNE（定向 update 写回，保 id/msg_seq）====================

    private List<Message> snipOrPrune(List<Message> messages, String sessionId,
                                      CompressionTier targetTier, EonProperties.Context ctx) {
        int tailStart = findProtectedTailStart(messages, ctx);
        int firstUser = findFirstUserIndex(messages);
        List<Message> processed = new ArrayList<>(messages.size());
        boolean changed = false;

        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (isProtected(i, tailStart, firstUser, m)) {
                processed.add(m);
                continue;
            }
            Long msgSeq = extractMsgSeq(m);
            // 决策单调推进：已达目标层级或更高的跳过
            CompressionTier existing = (msgSeq != null) ? compressionCache.getDecision(sessionId, msgSeq) : null;
            if (existing != null && existing.isAtLeast(targetTier)) {
                processed.add(m);
                continue;
            }
            // 执行压缩
            Message compressed = (targetTier == CompressionTier.PRUNE)
                    ? pruneMessage(m, sessionId, ctx.getArtifactThresholdChars())
                    : snipMessage(m);
            processed.add(compressed);
            changed = true;
            // 写回：定向 update（保留 id/msg_seq 稳定） 标记决策
            if (msgSeq != null) {
                try {
                    chatMessageMapper.updateContentById(msgSeq, serializeContent(compressed));
                    compressionCache.saveDecision(sessionId, msgSeq, targetTier);
                } catch (Exception e) {
                    log.warn("[Compression] 定向写回失败: msgSeq={}, err={}", msgSeq, e.getMessage());
                }
            }
        }
        return changed ? processed : messages;
    }

    private Message snipMessage(Message m) {
        String text = m.getText();
        if (text == null || text.length() <= SNIP_KEEP_HEAD_TAIL * 2) {
            return m;
        }
        String head = text.substring(0, SNIP_KEEP_HEAD_TAIL);
        String tail = text.substring(text.length() - SNIP_KEEP_HEAD_TAIL);
        String snipped = head + "\n...[SNIP 截断 " + (text.length() - SNIP_KEEP_HEAD_TAIL * 2) + " 字符]...\n" + tail;
        return rebuildWithText(m, snipped);
    }

    private Message pruneMessage(Message m, String sessionId, int threshold) {
        String text = m.getText();
        if (text == null || text.isBlank()) {
            return m;
        }
        if (text.length() <= threshold) {
            if (m.getMessageType() == MessageType.ASSISTANT) {
                return rebuildWithText(m, firstTwoSentences(text));
            }
            return m;
        }
        String uri;
        try {
            uri = artifactStore.save(sessionId, text);
        } catch (Exception e) {
            log.warn("[Compression] artifact 外置失败，降级硬截断: {}", e.getMessage());
            uri = "(外置失败)";
        }
        String placeholder = "[完整内容已外置: " + uri + "]";
        String pruned = (m.getMessageType() == MessageType.ASSISTANT)
                ? firstTwoSentences(text) + "\n" + placeholder
                : placeholder;
        return rebuildWithText(m, pruned);
    }

    // ==================== COMPACT（saveAll 写回：摘要 + 最近 3 轮）====================

    private List<Message> compact(List<Message> messages, String sessionId, EonProperties.Context ctx) {
        int tailStart = findProtectedTailStart(messages, ctx);
        int firstUser = findFirstUserIndex(messages);

        List<Message> toCompact = new ArrayList<>();
        List<Message> protectedTail = new ArrayList<>();
        List<Message> systemMsgs = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (i >= tailStart || i == firstUser) {
                protectedTail.add(m);
            } else if (m.getMessageType() == MessageType.SYSTEM) {
                systemMsgs.add(m);
            } else {
                toCompact.add(m);
            }
        }

        if (toCompact.isEmpty()) {
            return messages;
        }

        AgentContextSummary last = summaryMapper.selectLatestBySessionId(sessionId);
        String lastSummary = (last != null && last.getSummary() != null) ? last.getSummary() : "(首次摘要，无历史摘要)";

        String newSummary = generateSummary(lastSummary, toCompact);
        if (newSummary == null || newSummary.isBlank()) {
            log.warn("[Compression] COMPACT 摘要为空，保留原消息");
            return messages;
        }
        if (newSummary.contains("Nothing to save")) {
            newSummary = lastSummary;
        }

        try {
            AgentContextSummary s = new AgentContextSummary();
            s.setSessionId(sessionId);
            s.setSummary(newSummary);
            s.setBaseMessageId((long) tailStart);
            summaryMapper.insert(s);
        } catch (Exception e) {
            log.warn("[Compression] 摘要持久化失败（不影响本次压缩）: {}", e.getMessage());
        }

        // 重组：system + 摘要 SystemMessage + 保护区（含首条 user + 最近 3 轮）
        List<Message> result = new ArrayList<>();
        result.addAll(systemMsgs);
        result.add(new SystemMessage("## 历史对话摘要（COMPACT 产出）\n" + newSummary));
        result.addAll(protectedTail);

        // 写回 ChatMemory（saveAll replace：摘要 + 最近 3 轮替换全部历史）
        // 注意：replace 后消息 id 重新生成；但 COMPACT 后消息量骤降，下轮通常不再触发
        try {
            chatMemoryRepository.saveAll(sessionId, result);
            log.info("[Compression] COMPACT 写回 ChatMemory: {} 条", result.size());
        } catch (Exception e) {
            log.warn("[Compression] COMPACT 写回失败，仅 in-flight 生效: {}", e.getMessage());
        }
        return result;
    }

    private String generateSummary(String lastSummary, List<Message> delta) {
        try {
            String promptContent = configService.getPrompt(PromptKey.COMPACT_SUMMARY.getCode());
            if (promptContent == null || promptContent.isBlank()) {
                promptContent = "你是上下文压缩引擎，负责将长对话历史压缩为高密度摘要。";
            }
            String deltaText = formatForCompact(delta);
            return chatClientFactory.lightClient().prompt()
                    .system(promptContent)
                    .user("""
                            ## 上次摘要
                            %s

                            ## 新增对话（需并入摘要）
                            %s

                            请将「上次摘要 + 新增对话」合并为新摘要，固定结构：
                            # 原始目标
                            # 分阶段进展
                            # 文件中 ID（保留具体值）
                            # 待办
                            # 已放弃路径
                            若新增对话无可总结内容，仅回复：Nothing to save"""
                            .formatted(lastSummary, deltaText))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[Compression] COMPACT 摘要生成失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 工具方法 ====================

    private boolean isProtected(int index, int tailStart, int firstUser, Message m) {
        return index >= tailStart || index == firstUser || m.getMessageType() == MessageType.SYSTEM;
    }

    private int findProtectedTailStart(List<Message> messages, EonProperties.Context ctx) {
        int n = messages.size();
        int protectTokens = ctx.getProtectTailTokens();
        int byRounds = Math.max(0, n - PROTECT_ROUNDS * 2);
        int acc = 0;
        int byTokens = n;
        for (int i = n - 1; i >= 0; i--) {
            acc += tokenBudget.estimateMessage(messages.get(i));
            if (acc >= protectTokens) {
                byTokens = i;
                break;
            }
        }
        return Math.min(byRounds, byTokens);
    }

    private int findFirstUserIndex(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageType() == MessageType.USER) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 用新文本重建消息，保留原 metadata（含 msg_seq）。
     */
    private Message rebuildWithText(Message original, String newText) {
        Message result = switch (original.getMessageType()) {
            case USER -> new UserMessage(newText);
            case ASSISTANT -> new AssistantMessage(newText);
            case SYSTEM -> new SystemMessage(newText);
            case TOOL -> new UserMessage(newText); // S1 降级，S3 沿用
        };
        // 保留原 metadata（含 msg_seq）：构造后回填（Spring AI 消息 (String,Map) 构造不可用）
        if (original.getMetadata() != null && !original.getMetadata().isEmpty()) {
            result.getMetadata().putAll(original.getMetadata());
        }
        return result;
    }

    /**
     * 从消息 metadata 提取 msg_seq（= chat_message.id）。
     */
    private Long extractMsgSeq(Message m) {
        if (m.getMetadata() == null) {
            return null;
        }
        Object v = m.getMetadata().get(PgChatMemoryRepository.MSG_SEQ_KEY);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 序列化消息为 content JSON（与 PgChatMemoryRepository.serialize 格式一致，供 updateContentById）。
     */
    private String serializeContent(Message msg) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", msg.getMessageType().name());
            data.put("text", msg.getText() != null ? msg.getText() : "");
            if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
                data.put("metadata", new HashMap<>(msg.getMetadata()));
            }
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"type\":\"" + msg.getMessageType().name() + "\",\"text\":\"\"}";
        }
    }

    private String firstTwoSentences(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] parts = text.split("(?<=[。！？\\n])");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String p : parts) {
            if (!p.isBlank()) {
                sb.append(p);
                count++;
                if (count >= 2) {
                    break;
                }
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? text.substring(0, Math.min(200, text.length())) : result;
    }

    private String formatForCompact(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("[").append(m.getMessageType()).append("] ")
                    .append(m.getText() != null ? m.getText() : "")
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String extractSessionId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object v = context.get(ChatMemory.CONVERSATION_ID);
        return v != null ? v.toString() : null;
    }
}
