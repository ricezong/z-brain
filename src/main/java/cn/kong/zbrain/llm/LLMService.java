package cn.kong.zbrain.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 服务接口
 *
 * <p>基于 Spring AI 统一 AI 模型交互接口，提供同步与流式调用。</p>
 *
 * @author zbrain-team
 */
public interface LLMService {

    /**
     * 同步调用
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话（可选）
     * @return 生成的回答
     */
    String chat(String systemPrompt, String userPrompt, List<ChatMessage> history);

    /**
     * 流式调用（SSE）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param history      历史对话
     * @param onChunk      每个文本块的回调
     */
    void chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history,
                    Consumer<String> onChunk);

    /**
     * 简单调用（用于 Query 改写、HyDE、意图识别等轻量任务）
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
