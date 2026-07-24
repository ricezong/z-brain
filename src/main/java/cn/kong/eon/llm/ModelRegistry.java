package cn.kong.eon.llm;

import cn.kong.eon.persistence.entity.SysLlmModel;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 模型注册中心（纯模型生命周期管理，不掺对话）
 *
 * <p>职责：预加载、热更新四件套（register/evict/reload/reloadDefault）、缓存。
 * 模型构建逻辑只有一份（cacheEntry），ChatClientFactory 只组装不构建。</p>
 *
 * @author eon-team
 */
public interface ModelRegistry {

    /**
     * 获取 ChatModel（命中缓存返回，未命中懒加载）
     *
     * @param modelId 模型配置 ID，null 时返回默认 chat 模型
     */
    ChatModel getChatModel(Long modelId);

    /**
     * 启动预热所有活跃 chat 模型
     */
    void preload();

    /**
     * 新增/修改热更新
     */
    void register(SysLlmModel config);

    /**
     * 删除热更新
     */
    void evict(Long modelId);

    /**
     * 重载
     */
    void reload(Long modelId);

    /**
     * 默认模型变更
     */
    void reloadDefault();

    /**
     * 全量清缓存
     */
    void evictAll();
}
