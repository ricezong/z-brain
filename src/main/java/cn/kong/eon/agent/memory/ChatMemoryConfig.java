package cn.kong.eon.agent.memory;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置
 *
 * <p>组装三层组件：</p>
 * <ol>
 *   <li>{@link ChatMemoryRepository}：由持久层提供（{@link PgChatMemoryRepository}，Component 自动注册）</li>
 *   <li>{@link ChatMemory}：使用 {@link MessageWindowChatMemory}，maxMessages=200 滑动窗口</li>
 *   <li>{@link Advisor}：使用 {@link MessageChatMemoryAdvisor}，自动注入到 ChatClientFactory 的 Advisor 链</li>
 * </ol>
 *
 * <p>MessageChatMemoryAdvisor 继承 BaseAdvisor（before+after），
 * 在 before 阶段从 ChatMemory 加载历史消息并注入 prompt，
 * 在 after 阶段将本轮对话写入 ChatMemory。
 * conversationId 通过 context 传 {@code ChatMemory.CONVERSATION_ID} 参数。</p>
 *
 * @author eon-team
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * ChatMemory 滑动窗口（基于消息条数的窗口策略）
     *
     * <p>maxMessages=200 保证常规对话不截断，超限由压缩 Advisor 处理。</p>
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(200)
                .build();
    }

    /**
     * MessageChatMemoryAdvisor 注册为 Advisor Bean。
     *
     * <p>ChatClientFactory 通过 {@code List<Advisor>} 自动注入所有 Advisor Bean。
     * 作为 ChatClient 的 Advisor 链一环，按 order 排序，
     * 默认 {@code Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER}，在压缩 Advisor 之后执行。</p>
     */
    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
