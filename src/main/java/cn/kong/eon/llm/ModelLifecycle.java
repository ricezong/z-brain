package cn.kong.eon.llm;

/**
 * 模型生命周期事件（变更通知 factory 失效）
 *
 * <p>当模型配置发生变更时，由 {@code ConfigService} 发送此事件，
 * {@code ChatClientFactory} 监听后触发 {@link ModelRegistry#reload(Long)} 自动热更新。</p>
 *
 * <p>事件流转链路：</p>
 * <pre>
 * ConfigService.updateModelConfig()
 *   → publishEvent(ModelChangedEvent)
 *     → ChatClientFactory.onModelChanged()
 *       → ModelRegistry.reload(modelId)
 * </pre>
 *
 * @author eon-team
 */
public final class ModelLifecycle {

    private ModelLifecycle() {
    }

    /**
     * 模型配置变更事件
     *
     * @param modelId 变更的模型配置 ID
     */
    public record ModelChangedEvent(Long modelId) {}
}
