package cn.kong.zbrain.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 服务接口
 *
 * <p>基于 Spring AI 统一 AI 模型交互接口，提供同步与流式调用。</p>
 *
 * <p>模型生命周期管理（预加载、注册、移除、重载）见 {@link LLMModelRegistry}。</p>
 *
 * @author zbrain-team
 */
public interface LLMService {

    /**
     * 同步调用（使用默认模型）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话（可选）
     * @return 生成的回答
     */
    String chat(String systemPrompt, String userPrompt, List<ChatMessage> history);

    /**
     * 同步调用（指定模型）
     *
     * @param modelId      模型配置 ID，为 null 时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话（可选）
     * @return 生成的回答
     */
    String chat(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history);

    /**
     * 同步调用（指定模型 + 深度思考）
     *
     * @param modelId      模型配置 ID，为 null 时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话（可选）
     * @param thinking     是否启用深度思考模式
     * @return 生成的回答
     */
    String chat(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history, boolean thinking);

    /**
     * 流式调用（SSE，使用默认模型）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话
     * @param onChunk      每个文本块的回调
     */
    void chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history,
                    Consumer<String> onChunk);

    /**
     * 流式调用（SSE，指定模型）
     *
     * @param modelId      模型配置 ID，为 null 时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话
     * @param onChunk      每个文本块的回调
     */
    void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                    Consumer<String> onChunk);

    /**
     * 流式调用（SSE，指定模型 + 深度思考）
     *
     * @param modelId      模型配置 ID，为 null 时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话
     * @param thinking     是否启用深度思考模式
     * @param onChunk      每个文本块的回调
     */
    void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                    boolean thinking, Consumer<String> onChunk);

    /**
     * 流式调用（SSE，指定模型 + 深度思考 + Token 用量回调）
     *
     * <p>流式输出完成后，通过 {@code onUsage} 回调返回 Token 消耗统计。</p>
     *
     * @param modelId      模型配置 ID，为 null 时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话
     * @param thinking     是否启用深度思考模式
     * @param onChunk      每个文本块的回调
     * @param onUsage      Token 用量回调（流式结束后触发一次）
     */
    void chatStream(Long modelId, String systemPrompt, String userPrompt, List<ChatMessage> history,
                    boolean thinking, Consumer<String> onChunk, Consumer<org.springframework.ai.chat.metadata.Usage> onUsage);

    /**
     * 简单调用（用于 Query 改写、意图识别等轻量任务）
     *
     * @param prompt 提示词
     * @return 生成的回答
     */
    String simpleChat(String prompt);

    /**
     * 对话消息
     */
    record ChatMessage(String role, String content) {
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }
    }
}
