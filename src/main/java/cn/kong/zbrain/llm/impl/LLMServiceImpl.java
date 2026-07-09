package cn.kong.zbrain.llm.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.enums.ChatRole;
import cn.kong.zbrain.enums.ModelType;
import cn.kong.zbrain.llm.LLMModelRegistry;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.SysLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * LLM 服务实现（基于 Spring AI + 数据库模型配置）
 *
 * <p>统一封装大模型交互，支持同步与流式调用。
 * 模型配置从数据库 sys_llm_model 表读取，支持动态切换模型。</p>
 *
 * <p><b>模型注册中心</b>：系统启动时通过 {@link #preload()} 预加载所有活跃 chat 模型，
 * 对话时命中缓存直接返回（不再每次查库）。模型配置新增/修改/删除后，由
 * {@link #register}/{@link #reload}/{@link #evict}/{@link #reloadDefault} 进行细粒度热更新，
 * 无需重启、无需全量清缓存。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService, LLMModelRegistry {

    private final SysLlmModelService sysLlmModelService;

    /** 多模型缓存：模型配置 ID -> 缓存条目（按真实 ID 缓存，默认模型不再使用占位 key） */
    private final Map<Long, ModelCacheEntry> modelCache = new ConcurrentHashMap<>();

    /** 缓存的默认 chat 模型 ID，默认模型变更后通过 {@link #reloadDefault()} 刷新 */
    private volatile Long defaultChatModelId;

    /**
     * 模型缓存条目
     */
    private record ModelCacheEntry(ChatModel chatModel, ChatClient chatClient) {}

    // ==================== 模型注册中心（启动预加载 + 热更新） ====================

    /**
     * 系统启动后自动预加载所有活跃的 chat 模型，避免首次对话触发初始化耗时
     */
    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void preload() {
        log.info("[模型预加载] 开始从数据库加载 chat 模型配置...");
        try {
            List<SysLlmModel> models = sysLlmModelService.listByType(ModelType.CHAT.getCode());
            int activeCount = 0;
            for (SysLlmModel m : models) {
                if (Boolean.TRUE.equals(m.getIsActive())) {
                    cacheEntry(m);
                    activeCount++;
                }
            }
            // 解析默认模型指针
            SysLlmModel def = sysLlmModelService.getDefaultByType(ModelType.CHAT.getCode());
            defaultChatModelId = def != null ? def.getId() : null;
            log.info("[模型预加载] 完成：共 {} 个 chat 模型，活跃已注册 {} 个，默认模型 id={}",
                    models.size(), activeCount, defaultChatModelId);
        } catch (Exception e) {
            log.error("[模型预加载] 失败，将退化为首次调用时懒加载", e);
        }
    }

    @Override
    public void register(SysLlmModel modelConfig) {
        if (modelConfig == null || !ModelType.CHAT.getCode().equals(modelConfig.getModelType())) {
            return;
        }
        // 非活跃模型不进入缓存，并清理可能存在的旧缓存
        if (!Boolean.TRUE.equals(modelConfig.getIsActive())) {
            modelCache.remove(modelConfig.getId());
            return;
        }
        cacheEntry(modelConfig);
    }

    @Override
    public void evict(Long modelId) {
        if (modelId == null) {
            return;
        }
        modelCache.remove(modelId);
        if (modelId.equals(defaultChatModelId)) {
            defaultChatModelId = null;
        }
    }

    @Override
    public void reload(Long modelId) {
        if (modelId == null) {
            return;
        }
        try {
            SysLlmModel cfg = sysLlmModelService.getById(modelId);
            register(cfg);
        } catch (Exception e) {
            log.warn("重载模型失败，移除其缓存: id={}, err={}", modelId, e.getMessage());
            evict(modelId);
        }
    }

    @Override
    public void reloadDefault() {
        defaultChatModelId = null;
        try {
            SysLlmModel def = sysLlmModelService.getDefaultByType(ModelType.CHAT.getCode());
            if (def == null) {
                log.warn("未找到默认 chat 模型配置，默认模型指针已清空");
                return;
            }
            defaultChatModelId = def.getId();
            // 默认模型若尚未注册，则补充注册
            if (!modelCache.containsKey(def.getId())) {
                cacheEntry(def);
            }
        } catch (Exception e) {
            log.warn("重载默认模型失败: {}", e.getMessage());
        }
    }

    @Override
    public void clearCache() {
        synchronized (this) {
            modelCache.clear();
            defaultChatModelId = null;
        }
    }

    /**
     * 根据模型配置构建 ChatModel/ChatClient 并写入缓存
     *
     * <p>构建过程加锁，避免同一模型被并发重复构建。</p>
     */
    private void cacheEntry(SysLlmModel modelConfig) {
        synchronized (this) {
            log.info("注册 ChatModel: id={}, name={}, model={}, baseUrl={}",
                    modelConfig.getId(), modelConfig.getName(),
                    modelConfig.getModelName(), modelConfig.getBaseUrl());

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(modelConfig.getBaseUrl())
                    .apiKey(modelConfig.getApiKey())
                    .build();

            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(modelConfig.getModelName());

            if (modelConfig.getTemperature() != null) {
                optionsBuilder.temperature(modelConfig.getTemperature());
            }
            if (modelConfig.getMaxTokens() != null) {
                optionsBuilder.maxTokens(modelConfig.getMaxTokens());
            }

            ChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(optionsBuilder.build())
                    .build();

            ChatClient chatClient = ChatClient.builder(chatModel).build();
            modelCache.put(modelConfig.getId(), new ModelCacheEntry(chatModel, chatClient));
        }
    }

    // ==================== 模型获取（纯缓存读取，命中时不查库） ====================

    /**
     * 获取 ChatModel 实例
     *
     * <p>命中缓存时直接返回（不再每次查库校验），缓存新鲜度由
     * {@link #register}/{@link #reload}/{@link #evict} 热更新保证。
     * 未命中时按配置懒加载（兜底场景）。</p>
     *
     * @param modelId 模型配置 ID，为 null 时使用默认 chat 模型
     */
    private ChatModel getChatModel(Long modelId) {
        Long effectiveId = (modelId != null) ? modelId : resolveDefaultId();
        ModelCacheEntry entry = modelCache.get(effectiveId);
        if (entry != null) {
            return entry.chatModel();
        }

        // 缓存未命中（如未预加载成功、或被淘汰）：按配置懒加载兜底
        SysLlmModel modelConfig = resolveModelConfig(modelId);
        if (modelConfig == null) {
            throw new BusinessException("未找到" + (modelId != null ? "ID=" + modelId + " 的" : "默认的")
                    + " chat 模型配置，请在系统配置中添加");
        }
        if (!modelCache.containsKey(modelConfig.getId())) {
            cacheEntry(modelConfig);
        }
        entry = modelCache.get(modelConfig.getId());
        if (entry == null) {
            throw new BusinessException("ChatModel 初始化失败: modelId=" + modelId);
        }
        return entry.chatModel();
    }

    /**
     * 解析默认模型 ID（带缓存指针，避免每次查库）
     */
    private Long resolveDefaultId() {
        if (defaultChatModelId != null) {
            return defaultChatModelId;
        }
        SysLlmModel def = sysLlmModelService.getDefaultByType(ModelType.CHAT.getCode());
        if (def == null) {
            throw new BusinessException("未找到默认的 chat 模型配置，请在系统配置中添加");
        }
        defaultChatModelId = def.getId();
        if (!modelCache.containsKey(def.getId())) {
            cacheEntry(def);
        }
        return defaultChatModelId;
    }

    /**
     * 解析模型配置：modelId 不为 null 时按 ID 查询，否则查默认 chat 模型
     *
     * <p>仅在缓存未命中的兜底路径使用。按 ID 查询失败时降级为默认模型。</p>
     */
    private SysLlmModel resolveModelConfig(Long modelId) {
        if (modelId != null) {
            try {
                return sysLlmModelService.getById(modelId);
            } catch (Exception e) {
                log.warn("按 ID 查询模型配置失败: {}, 降级为默认模型", modelId);
                return sysLlmModelService.getDefaultByType(ModelType.CHAT.getCode());
            }
        }
        return sysLlmModelService.getDefaultByType(ModelType.CHAT.getCode());
    }

    private ChatClient getChatClient(Long modelId) {
        Long effectiveId = (modelId != null) ? modelId : resolveDefaultId();
        ModelCacheEntry entry = modelCache.get(effectiveId);
        if (entry == null) {
            getChatModel(modelId); // 确保已初始化
            entry = modelCache.get(effectiveId);
        }
        return entry.chatClient();
    }

    // ==================== 同步调用 ====================

    @Override
    public String chat(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        return chat(null, systemPrompt, userPrompt, history);
    }

    @Override
    public String chat(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history) {
        return chat(modelId, systemPrompt, userPrompt, history, false);
    }

    @Override
    public String chat(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history, boolean thinking) {
        try {
            String effectiveSystemPrompt = applyThinking(systemPrompt, thinking);
            List<Message> messages = buildMessages(effectiveSystemPrompt, history, userPrompt);
            Prompt prompt = new Prompt(messages);
            return getChatModel(modelId).call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("LLM 同步调用失败: modelId={}, thinking={}", modelId, thinking, e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 流式调用 ====================

    @Override
    public void chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history,
                           Consumer<String> onChunk) {
        chatStream(null, systemPrompt, userPrompt, history, onChunk);
    }

    @Override
    public void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                           Consumer<String> onChunk) {
        chatStream(modelId, systemPrompt, userPrompt, history, false, onChunk);
    }

    @Override
    public void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                           boolean thinking, Consumer<String> onChunk) {
        chatStream(modelId, systemPrompt, userPrompt, history, thinking, onChunk, null);
    }

    @Override
    public void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                           boolean thinking, Consumer<String> onChunk, Consumer<Usage> onUsage) {
        try {
            String effectiveSystemPrompt = applyThinking(systemPrompt, thinking);
            List<Message> messages = buildMessages(effectiveSystemPrompt, history, userPrompt);
            Prompt prompt = new Prompt(messages);
            Usage capturedUsage = null;
            for (ChatResponse chatResponse : getChatModel(modelId).stream(prompt).toIterable()) {
                if (chatResponse == null || chatResponse.getResult() == null
                        || chatResponse.getResult().getOutput() == null) {
                    continue;
                }
                String text = chatResponse.getResult().getOutput().getText();
                if (text != null && !text.isEmpty()) {
                    onChunk.accept(text);
                }
                // 捕获最后一个 chunk 中的 usage（OpenAI 兼容 API 在流式末尾返回）
                if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                    capturedUsage = chatResponse.getMetadata().getUsage();
                }
            }
            if (onUsage != null && capturedUsage != null) {
                onUsage.accept(capturedUsage);
            }
        } catch (Exception e) {
            log.error("LLM 流式调用失败: modelId={}, thinking={}", modelId, thinking, e);
            throw new BusinessException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 简单调用 ====================

    @Override
    public String simpleChat(String prompt) {
        try {
            return getChatClient(null).prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM 简单调用失败", e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部方法 ====================

    private List<Message> buildMessages(String systemPrompt, List<ChatMessage> history, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        if (history != null) {
            for (ChatMessage msg : history) {
                if (ChatRole.USER.getCode().equals(msg.role())) {
                    messages.add(new UserMessage(msg.content()));
                } else if (ChatRole.ASSISTANT.getCode().equals(msg.role())) {
                    messages.add(new AssistantMessage(msg.content()));
                }
            }
        }
        messages.add(new UserMessage(userPrompt));
        return messages;
    }

    /**
     * 深度思考模式：在 system prompt 末尾追加思考指令
     *
     * <p>引导模型在回答前进行逐步推理，适用于复杂分析、多角度对比等场景。</p>
     */
    private static final String THINKING_SUFFIX = "\n\n"
            + "## 深度思考模式已启用\n"
            + "请在回答前进行深入的逐步推理：\n"
            + "1. 先分析问题的核心诉求和关键约束条件\n"
            + "2. 从多个角度拆解问题，列出可能的解决思路\n"
            + "3. 对每种思路进行简要评估，选择最优方案\n"
            + "4. 最后给出结构清晰、逻辑严谨的完整回答\n"
            + "推理过程用 <thinking>...</thinking> 标签包裹，最终回答放在标签之外。";

    private String applyThinking(String systemPrompt, boolean thinking) {
        if (thinking && systemPrompt != null) {
            return systemPrompt + THINKING_SUFFIX;
        }
        return systemPrompt;
    }
}
