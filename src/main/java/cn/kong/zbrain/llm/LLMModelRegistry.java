package cn.kong.zbrain.llm;

import cn.kong.zbrain.entity.SysLlmModel;

/**
 * LLM 模型注册中心接口
 *
 * <p>负责 ChatModel 实例的生命周期管理：预加载、注册、移除、重载。
 * 与 {@link LLMService} 的对话能力分离，遵循接口隔离原则。</p>
 *
 * <p>系统启动时通过 {@link #preload} 预加载所有活跃 chat 模型，
 * 对话时命中缓存直接返回。模型配置变更后通过 {@link #register}/{@link #reload}/
 * {@link #evict}/{@link #reloadDefault} 进行细粒度热更新。</p>
 *
 * @author zbrain-team
 */
public interface LLMModelRegistry {

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
}
