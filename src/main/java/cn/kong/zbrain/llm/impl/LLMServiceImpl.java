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
import java.util.function.Consumer;

/**
 * LLM 服务实现（基于 Spring AI + 数据库模型配置）
 *
 * <p>统一封装大模型交互，支持同步与流式调用。
 * 模型配置从数据库 sys_llm_model 表读取，支持动态切换模型。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final SysLlmModelService sysLlmModelService;

    /** 缓存的 ChatModel 实例，配置变更后通过 clearCache() 失效 */
    private volatile ChatModel cachedChatModel;
    private volatile ChatClient cachedChatClient;
    private volatile Long cachedModelId;

    /**
     * 获取默认 chat 模型的 ChatModel 实例
     * 如果数据库中的配置 ID 未变，则复用缓存的实例
     */
    private ChatModel getChatModel() {
        SysLlmModel modelConfig = sysLlmModelService.getDefaultByType("chat");
        if (modelConfig == null) {
            throw new BusinessException("未找到默认的 chat 模型配置，请在系统配置中添加");
        }

        // 配置未变更，复用缓存
        if (cachedChatModel != null && modelConfig.getId().equals(cachedModelId)) {
            return cachedChatModel;
        }

        synchronized (this) {
            if (cachedChatModel != null && modelConfig.getId().equals(cachedModelId)) {
                return cachedChatModel;
            }

            log.info("初始化 ChatModel: name={}, model={}, baseUrl={}",
                    modelConfig.getName(), modelConfig.getModelName(), modelConfig.getBaseUrl());

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

            cachedChatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(optionsBuilder.build())
                    .build();

            cachedChatClient = ChatClient.builder(cachedChatModel).build();
            cachedModelId = modelConfig.getId();

            return cachedChatModel;
        }
    }

    private ChatClient getChatClient() {
        getChatModel(); // 确保已初始化
        return cachedChatClient;
    }

    /**
     * 清除缓存的 ChatModel（配置变更时调用） — 实现自 LLMService.clearCache()
     */
    @Override
    public void clearCache() {
        synchronized (this) {
            cachedChatModel = null;
            cachedChatClient = null;
            cachedModelId = null;
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        try {
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

            Prompt prompt = new Prompt(messages);
            return getChatModel().call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("LLM 同步调用失败", e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history,
                           Consumer<String> onChunk) {
        try {
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

            Prompt prompt = new Prompt(messages);
            getChatModel().stream(prompt).toIterable().forEach(chatResponse -> {
                String text = chatResponse.getResult().getOutput().getText();
                if (text != null && !text.isEmpty()) {
                    onChunk.accept(text);
                }
            });
        } catch (Exception e) {
            log.error("LLM 流式调用失败", e);
            throw new BusinessException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String simpleChat(String prompt) {
        try {
            return getChatClient().prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM 简单调用失败", e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
}
