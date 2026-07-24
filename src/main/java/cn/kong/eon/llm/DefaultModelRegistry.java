package cn.kong.eon.llm;

import cn.kong.eon.common.exception.BusinessException;
import cn.kong.eon.config.ConfigService;
import cn.kong.eon.persistence.entity.SysLlmModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型注册中心实现（重写：去 LLMService 耦合、统一构建）
 *
 * <p>模型构建逻辑只有一份（cacheEntry），ChatClientFactory 直接复用本注册中心缓存的 ChatModel。
 * 不再缓存 ChatClient（ChatClient 轻量，由 Factory 每次按 Advisor 链组装）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModelRegistry implements ModelRegistry {

    private final ConfigService configService;

    /** 模型缓存：配置 ID → ChatModel */
    private final Map<Long, ChatModel> modelCache = new ConcurrentHashMap<>();

    /** 默认 chat 模型 ID */
    private volatile Long defaultChatModelId;

    // ==================== 启动预加载 + 热更新 ====================

    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void preload() {
        log.info("[ModelRegistry] 预加载开始...");
        try {
            List<SysLlmModel> models = configService.listActiveModels(ModelType.CHAT.getCode());
            int activeCount = 0;
            for (SysLlmModel m : models) {
                if (Boolean.TRUE.equals(m.getIsActive())) {
                    cacheEntry(m);
                    activeCount++;
                }
            }
            SysLlmModel def = configService.getDefaultModel(ModelType.CHAT.getCode());
            defaultChatModelId = def != null ? def.getId() : null;
            log.info("[ModelRegistry] 预加载完成: {} 个活跃模型, 默认 id={}", activeCount, defaultChatModelId);
        } catch (Exception e) {
            log.error("[ModelRegistry] 预加载失败，退化为懒加载", e);
        }
    }

    @Override
    public void register(SysLlmModel config) {
        if (config == null || !ModelType.CHAT.getCode().equals(config.getModelType())) {
            return;
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            modelCache.remove(config.getId());
            return;
        }
        cacheEntry(config);
    }

    @Override
    public void evict(Long modelId) {
        if (modelId != null) {
            modelCache.remove(modelId);
            if (modelId.equals(defaultChatModelId)) {
                defaultChatModelId = null;
            }
        }
    }

    @Override
    public void reload(Long modelId) {
        if (modelId == null) return;
        try {
            SysLlmModel cfg = configService.getModelConfig(modelId);
            register(cfg);
        } catch (Exception e) {
            log.warn("[ModelRegistry] reload 失败: id={}, err={}", modelId, e.getMessage());
            evict(modelId);
        }
    }

    @Override
    public void reloadDefault() {
        defaultChatModelId = null;
        try {
            SysLlmModel def = configService.getDefaultModel(ModelType.CHAT.getCode());
            if (def == null) {
                log.warn("[ModelRegistry] 未找到默认 chat 模型");
                return;
            }
            defaultChatModelId = def.getId();
            if (!modelCache.containsKey(def.getId())) {
                cacheEntry(def);
            }
        } catch (Exception e) {
            log.warn("[ModelRegistry] reloadDefault 失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        modelCache.clear();
        defaultChatModelId = null;
    }

    // ==================== 模型获取 ====================

    @Override
    public ChatModel getChatModel(Long modelId) {
        Long effectiveId = (modelId != null) ? modelId : resolveDefaultId();
        ChatModel cached = modelCache.get(effectiveId);
        if (cached != null) {
            return cached;
        }
        // 懒加载
        SysLlmModel cfg = (modelId != null)
                ? configService.getModelConfig(modelId)
                : configService.getDefaultModel(ModelType.CHAT.getCode());
        if (cfg == null) {
            throw new BusinessException("未找到" + (modelId != null ? "ID=" + modelId : "默认") + " chat 模型配置");
        }
        if (!modelCache.containsKey(cfg.getId())) {
            cacheEntry(cfg);
        }
        return modelCache.get(cfg.getId());
    }

    private Long resolveDefaultId() {
        if (defaultChatModelId != null) {
            return defaultChatModelId;
        }
        SysLlmModel def = configService.getDefaultModel(ModelType.CHAT.getCode());
        if (def == null) {
            throw new BusinessException("未找到默认 chat 模型配置");
        }
        defaultChatModelId = def.getId();
        if (!modelCache.containsKey(def.getId())) {
            cacheEntry(def);
        }
        return defaultChatModelId;
    }

    // ==================== 统一构建（唯一构建点） ====================

    /**
     * 构建 ChatModel 并写入缓存（加锁防并发重复构建）
     */
    private synchronized void cacheEntry(SysLlmModel cfg) {
        log.info("[ModelRegistry] 构建 ChatModel: id={}, name={}, model={}",
                cfg.getId(), cfg.getName(), cfg.getModelName());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(cfg.getModelName());
        if (cfg.getTemperature() != null) {
            optionsBuilder.temperature(cfg.getTemperature());
        }
        if (cfg.getMaxTokens() != null) {
            optionsBuilder.maxTokens(cfg.getMaxTokens());
        }

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        modelCache.put(cfg.getId(), chatModel);
    }
}
