package cn.kong.zbrain.llm.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.llm.LLMService;
import cn.kong.zbrain.service.SysLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
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
 * <p>多模型缓存：每个模型配置 ID 对应独立的 ChatModel/ChatClient 实例，
 * 通过 {@link #getChatModel(Long)} 按 ID 获取，modelId 为 null 时使用默认模型。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final SysLlmModelService sysLlmModelService;

    /** 多模型缓存：modelId -> 缓存条目 */
    private final Map<Long, ModelCacheEntry> modelCache = new ConcurrentHashMap<>();

    /** 默认模型的缓存 key */
    private static final Long DEFAULT_KEY = -1L;

    /**
     * 模型缓存条目
     */
    private record ModelCacheEntry(ChatModel chatModel, ChatClient chatClient, Long modelConfigId) {}

    /**
     * 获取 ChatModel 实例
     *
     * @param modelId 模型配置 ID，为 null 时使用默认 chat 模型
     */
    private ChatModel getChatModel(Long modelId) {
        Long cacheKey = modelId != null ? modelId : DEFAULT_KEY;

        ModelCacheEntry entry = modelCache.get(cacheKey);
        if (entry != null) {
            // 校验数据库中的配置 ID 是否变化
            SysLlmModel current = resolveModelConfig(modelId);
            if (current != null && current.getId().equals(entry.modelConfigId())) {
                return entry.chatModel();
            }
        }

        // 需要创建/刷新缓存
        SysLlmModel modelConfig = resolveModelConfig(modelId);
        if (modelConfig == null) {
            throw new BusinessException("未找到" + (modelId != null ? "ID=" + modelId + " 的" : "默认的")
                    + " chat 模型配置，请在系统配置中添加");
        }

        synchronized (this) {
            entry = modelCache.get(cacheKey);
            if (entry != null && modelConfig.getId().equals(entry.modelConfigId())) {
                return entry.chatModel();
            }

            log.info("初始化 ChatModel: id={}, name={}, model={}, baseUrl={}",
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
            entry = new ModelCacheEntry(chatModel, chatClient, modelConfig.getId());
            modelCache.put(cacheKey, entry);

            return chatModel;
        }
    }

    /**
     * 解析模型配置：modelId 不为 null 时按 ID 查询，否则查默认 chat 模型
     */
    private SysLlmModel resolveModelConfig(Long modelId) {
        if (modelId != null) {
            try {
                return sysLlmModelService.getById(modelId);
            } catch (Exception e) {
                log.warn("按 ID 查询模型配置失败: {}, 降级为默认模型", modelId);
                return sysLlmModelService.getDefaultByType("chat");
            }
        }
        return sysLlmModelService.getDefaultByType("chat");
    }

    private ChatClient getChatClient(Long modelId) {
        Long cacheKey = modelId != null ? modelId : DEFAULT_KEY;
        getChatModel(modelId); // 确保已初始化
        return modelCache.get(cacheKey).chatClient();
    }

    @Override
    public void clearCache() {
        synchronized (this) {
            modelCache.clear();
        }
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
        try {
            String effectiveSystemPrompt = applyThinking(systemPrompt, thinking);
            List<Message> messages = buildMessages(effectiveSystemPrompt, history, userPrompt);
            Prompt prompt = new Prompt(messages);
            getChatModel(modelId).stream(prompt).toIterable().forEach(chatResponse -> {
                if (chatResponse == null || chatResponse.getResult() == null
                        || chatResponse.getResult().getOutput() == null) {
                    return;
                }
                String text = chatResponse.getResult().getOutput().getText();
                if (text != null && !text.isEmpty()) {
                    onChunk.accept(text);
                }
            });
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
                if ("user".equals(msg.role())) {
                    messages.add(new UserMessage(msg.content()));
                } else if ("assistant".equals(msg.role())) {
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
