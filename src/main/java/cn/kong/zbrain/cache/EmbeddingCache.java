package cn.kong.zbrain.cache;

import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 缓存
 *
 * <p>缓存文本到向量的映射，避免重复调用百炼 SDK，节省成本。</p>
 * <p>Key 规范：zbrain:embedding:{sha256(text)} -> vector</p>
 * <p>TTL：7 天</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ZBrainProperties properties;

    private static final String KEY_PREFIX = "embedding:";

    /**
     * 构建缓存 Key
     */
    private String buildKey(String text) {
        return properties.getCache().getPrefix() + KEY_PREFIX +
               CommonUtils.sha256(text);
    }

    /**
     * 获取单个文本的向量缓存
     *
     * @return 向量字符串，未命中返回 null
     */
    public String get(String text) {
        try {
            Object value = redisTemplate.opsForValue().get(buildKey(text));
            return value == null ? null : value.toString();
        } catch (Exception e) {
            log.warn("读取 Embedding 缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 批量获取向量缓存
     *
     * @param texts 文本列表
     * @return 文本到向量的映射（仅包含命中的）
     */
    public Map<String, String> batchGet(List<String> texts) {
        Map<String, String> result = new HashMap<>();
        for (String text : texts) {
            String vector = get(text);
            if (vector != null) {
                result.put(text, vector);
            }
        }
        return result;
    }

    /**
     * 写入单个缓存
     */
    public void put(String text, String vector) {
        try {
            redisTemplate.opsForValue().set(buildKey(text), vector,
                    Duration.ofSeconds(properties.getCache().getEmbeddingTtl()));
        } catch (Exception e) {
            log.warn("写入 Embedding 缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 批量写入缓存
     */
    public void batchPut(Map<String, String> textToVector) {
        textToVector.forEach(this::put);
    }

    /**
     * 删除缓存
     */
    public void evict(String text) {
        try {
            redisTemplate.delete(buildKey(text));
        } catch (Exception e) {
            log.warn("删除 Embedding 缓存失败: {}", e.getMessage());
        }
    }
}
