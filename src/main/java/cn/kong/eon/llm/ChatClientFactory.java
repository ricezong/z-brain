package cn.kong.eon.llm;

import cn.kong.eon.config.ConfigService;
import cn.kong.eon.persistence.entity.SysLlmModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ChatClient 工厂（重写：复用 Registry 缓存，不重复 buildChatModel）
 *
 * <p>按用途产出两类 ChatClient：</p>
 * <ul>
 *   <li><b>mainClient</b>：主模型 + 完整 Advisor 链（ChatClient 轻量，每次组装）</li>
 *   <li><b>lightClient</b>：辅助模型，裸调用（无 Advisor）</li>
 * </ul>
 *
 * <p>模型构建全在 ModelRegistry.cacheEntry 一处，本类只组装不构建。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientFactory {

    private final ModelRegistry modelRegistry;
    private final ConfigService configService;
    private final List<Advisor> advisors;

    /**
     * 主模型 ChatClient：主 chat 模型 + 完整 Advisor 链
     */
    public ChatClient mainClient() {
        ChatModel chatModel = modelRegistry.getChatModel(null);
        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisors.toArray(Advisor[]::new))
                .build();
    }

    /**
     * 主模型 ChatClient（指定模型 ID）
     */
    public ChatClient mainClient(Long modelId) {
        ChatModel chatModel = modelRegistry.getChatModel(modelId);
        return ChatClient.builder(chatModel)
                .defaultAdvisors(advisors.toArray(Advisor[]::new))
                .build();
    }

    /**
     * 辅助模型 ChatClient：chat_light 默认模型，裸调用
     *
     * <p>未配置 chat_light 时降级复用主模型。ChatModel 复用 Registry 缓存，不重复构建。</p>
     */
    public ChatClient lightClient() {
        SysLlmModel cfg = configService.getDefaultModel(ModelType.CHAT_LIGHT.getCode());
        if (cfg == null) {
            log.debug("[ChatClientFactory] 未配置 chat_light，降级使用主 chat 模型");
            return ChatClient.builder(modelRegistry.getChatModel(null)).build();
        }
        // 复用 Registry 缓存的 ChatModel（不自己 build）
        return ChatClient.builder(modelRegistry.getChatModel(cfg.getId())).build();
    }

    /**
     * 监听模型配置变更事件，自动失效 Registry 缓存
     */
    @EventListener
    public void onModelChanged(ModelLifecycle.ModelChangedEvent event) {
        log.info("[ChatClientFactory] 模型变更通知: id={}, 触发 Registry reload", event.modelId());
        modelRegistry.reload(event.modelId());
    }
}
