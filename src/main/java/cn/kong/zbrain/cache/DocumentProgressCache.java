package cn.kong.zbrain.cache;

import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.dto.response.DocumentProgressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文档处理进度缓存
 *
 * <p>用于前端轮询文档处理进度，避免长连接断开导致进度丢失。</p>
 * <p>Key 规范：zbrain:doc:progress:{documentId}</p>
 * <p>TTL：1 天</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProgressCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ZBrainProperties properties;

    private static final String KEY_PREFIX = "doc:progress:";

    private String buildKey(Long documentId) {
        return properties.getCache().getPrefix() + KEY_PREFIX + documentId;
    }

    /**
     * 更新文档处理进度
     */
    public void update(Long documentId, DocumentProgressResponse progress) {
        try {
            redisTemplate.opsForValue().set(buildKey(documentId), progress,
                    Duration.ofSeconds(properties.getCache().getDocProgressTtl()));
        } catch (Exception e) {
            log.warn("更新文档进度缓存失败: docId={}, error={}", documentId, e.getMessage());
        }
    }

    /**
     * 获取文档处理进度
     */
    public DocumentProgressResponse get(Long documentId) {
        try {
            Object value = redisTemplate.opsForValue().get(buildKey(documentId));
            if (value instanceof DocumentProgressResponse resp) {
                return resp;
            }
            return null;
        } catch (Exception e) {
            log.warn("读取文档进度缓存失败: docId={}, error={}", documentId, e.getMessage());
            return null;
        }
    }

    /**
     * 删除进度缓存
     */
    public void remove(Long documentId) {
        try {
            redisTemplate.delete(buildKey(documentId));
        } catch (Exception e) {
            log.warn("删除文档进度缓存失败: docId={}, error={}", documentId, e.getMessage());
        }
    }
}
