package cn.kong.eon.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cn.kong.eon.persistence.entity.SysLlmModel;
import cn.kong.eon.persistence.entity.SysPrompt;
import cn.kong.eon.persistence.entity.SysApiConfig;
import cn.kong.eon.persistence.mapper.SysPromptMapper;
import cn.kong.eon.persistence.mapper.SysLlmModelMapper;
import cn.kong.eon.persistence.mapper.SysApiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.kong.eon.llm.ModelLifecycle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 统一配置读写入口（Caffeine 本地缓存 + TTL + 变更失效）
 *
 * <p>替代旧工程 SysPromptService/SysLlmModelService/SysApiConfigService 各自直连 mapper 的散落模式。
 * 所有配置读写统一经此入口，命中缓存返回，未命中查库+缓存。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SysPromptMapper promptMapper;
    private final SysLlmModelMapper llmModelMapper;
    private final SysApiConfigMapper apiConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 提示词缓存：key → content，TTL 10min */
    private final Cache<String, String> promptCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(200)
            .build();

    /** 模型配置缓存：id → SysLlmModel，TTL 10min */
    private final Cache<Long, SysLlmModel> modelCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(100)
            .build();

    // ==================== Prompt ====================

    /**
     * 读取提示词内容（命中缓存返回，未命中查库+缓存）
     */
    public String getPrompt(String key) {
        return promptCache.get(key, k -> {
            SysPrompt prompt = promptMapper.selectByKey(k);
            return prompt != null ? prompt.getContent() : null;
        });
    }

    /**
     * 更新提示词（update DB + 失效缓存）
     */
    public void updatePrompt(Long id, String key, String content) {
        SysPrompt prompt = new SysPrompt();
        prompt.setId(id);
        prompt.setContent(content);
        promptMapper.update(prompt);
        promptCache.invalidate(key);
        log.info("[ConfigService] 提示词已更新并失效缓存: key={}", key);
    }

    // ==================== Model Config ====================

    /**
     * 按 ID 获取模型配置（命中缓存返回）
     */
    public SysLlmModel getModelConfig(Long id) {
        return modelCache.get(id, k -> llmModelMapper.selectById(k));
    }

    /**
     * 按 type 获取默认模型配置
     */
    public SysLlmModel getDefaultModel(String modelType) {
        return llmModelMapper.selectDefaultByType(modelType);
    }

    /**
     * 按 type 获取所有活跃模型
     */
    public java.util.List<SysLlmModel> listActiveModels(String modelType) {
        return llmModelMapper.selectByType(modelType);
    }

    /**
     * 获取所有模型配置（管理端列表用，不走缓存）
     */
    public java.util.List<SysLlmModel> listAllModels(String modelType) {
        if (modelType != null && !modelType.isBlank()) {
            return llmModelMapper.selectByType(modelType);
        }
        return llmModelMapper.selectAll();
    }

    /**
     * 新增模型配置（insert DB + 失效缓存）
     */
    public void createModelConfig(SysLlmModel config) {
        llmModelMapper.insert(config);
        invalidateAllModels();
        log.info("[ConfigService] 模型配置已新增: name={}", config.getName());
    }

    /**
     * 删除模型配置（delete DB + 失效缓存 + evict Registry）
     */
    public void deleteModelConfig(Long id) {
        llmModelMapper.deleteById(id);
        modelCache.invalidate(id);
        eventPublisher.publishEvent(new cn.kong.eon.llm.ModelLifecycle.ModelChangedEvent(id));
        log.info("[ConfigService] 模型配置已删除: id={}", id);
    }

    /**
     * 获取所有提示词（管理端列表用，不走缓存）
     */
    public java.util.List<SysPrompt> listAllPrompts() {
        return sysPromptMapperListAll();
    }

    private java.util.List<SysPrompt> sysPromptMapperListAll() {
        return promptMapper.selectList();
    }

    /**
     * 更新模型配置（update DB + 失效缓存 + 发事件触发 Registry reload）
     */
    public void updateModelConfig(Long id, SysLlmModel config) {
        config.setId(id);
        llmModelMapper.update(config);
        modelCache.invalidate(id);
        eventPublisher.publishEvent(new cn.kong.eon.llm.ModelLifecycle.ModelChangedEvent(id));
        log.info("[ConfigService] 模型配置已更新并触发 reload: id={}", id);
    }

    // ==================== API Config ====================

    /**
     * 按 type 获取 API 配置
     */
    public SysApiConfig getApiConfig(String configType) {
        return apiConfigMapper.selectByType(configType);
    }

    // ==================== 缓存管理 ====================

    /**
     * 失效所有提示词缓存
     */
    public void invalidateAllPrompts() {
        promptCache.invalidateAll();
    }

    /**
     * 失效所有模型配置缓存
     */
    public void invalidateAllModels() {
        modelCache.invalidateAll();
    }

    /**
     * 模型配置变更事件（由 ModelRegistry 监听自动 reload）
     * @see ModelLifecycle.ModelChangedEvent
     */
}
