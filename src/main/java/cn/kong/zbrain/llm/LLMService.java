package cn.kong.zbrain.llm;

import cn.kong.zbrain.entity.SysLlmModel;

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
     * 简单调用（用于 Query 改写、HyDE、意图识别等轻量任务）
     *
     * @param prompt 提示词
     * @return 生成的回答
     */
    String simpleChat(String prompt);

    /**
     * 系统启动时预加载所有活跃的 chat 模型到缓存
     *
     * <p>避免首次对话触发模型初始化的耗时。由 {@code ApplicationReadyEvent} 自动触发。</p>
     */
    void preload();

    /**
     * 注册（或刷新）单个模型实例到缓存
     *
     * <p>用于模型新增/修改后的热更新。仅注册活跃的 chat 类型模型，
     * 非活跃模型会被从缓存中移除。</p>
     *
     * @param modelConfig 模型配置
     */
    void register(SysLlmModel modelConfig);

    /**
     * 从缓存中移除指定模型实例
     *
     * <p>用于模型删除后的热更新。</p>
     *
     * @param modelId 模型配置 ID
     */
    void evict(Long modelId);

    /**
     * 从数据库重新加载指定模型并刷新缓存
     *
     * <p>用于模型修改后的热更新，等价于 evict + register。</p>
     *
     * @param modelId 模型配置 ID
     */
    void reload(Long modelId);

    /**
     * 重新解析默认 chat 模型
     *
     * <p>默认模型变更（设为默认 / 默认模型被删除 / 默认模型配置修改）后调用，
     * 刷新内部缓存的默认模型指针。</p>
     */
    void reloadDefault();

    /**
     * 清除所有缓存的 ChatModel/ChatClient 实例
     *
     * <p>全量重置，一般用于调试或异常恢复。日常模型变更请使用
     * {@link #register} / {@link #reload} / {@link #evict} 进行细粒度热更新。</p>
     */
    void clearCache();

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
