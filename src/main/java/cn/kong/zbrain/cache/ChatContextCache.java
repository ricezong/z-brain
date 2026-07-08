package cn.kong.zbrain.cache;

import cn.kong.zbrain.config.ZBrainProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文缓存
 *
 * <p>存储多轮对话短期上下文，TTL 2 小时，冷热分离设计。</p>
 * <p>Key 规范：zbrain:chat:context:{sessionId}</p>
 *
 * <p>使用 StringRedisTemplate + 独立 ObjectMapper 序列化，
 * 避免 RedisTemplate 全局 default typing 与 record 类型的兼容问题。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextCache {

    private final StringRedisTemplate redisTemplate;
    private final ZBrainProperties properties;

    private static final String KEY_PREFIX = "chat:context:";

    /** 独立 ObjectMapper，不启用 default typing，避免 record 序列化兼容问题 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<ChatMessage> MSG_TYPE = new TypeReference<>() {};

    /**
     * 单条对话消息
     */
    public record ChatMessage(String role, String content) {
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    private String buildKey(String sessionId) {
        return properties.getCache().getPrefix() + KEY_PREFIX + sessionId;
    }

    /**
     * 追加一条消息到会话上下文
     */
    public void appendMessage(String sessionId, ChatMessage message) {
        try {
            String key = buildKey(sessionId);
            String json = OBJECT_MAPPER.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, Duration.ofSeconds(properties.getCache().getChatContextTtl()));
        } catch (Exception e) {
            log.warn("写入对话上下文失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取最近 N 轮对话
     */
    public List<ChatMessage> getRecentMessages(String sessionId, int rounds) {
        try {
            String key = buildKey(sessionId);
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return new ArrayList<>();
            }
            // 每轮 = user + assistant，取最近 rounds*2 条
            long end = size - 1;
            long start = Math.max(0, size - (long) rounds * 2);
            List<String> raw = redisTemplate.opsForList().range(key, start, end);
            if (raw == null) {
                return new ArrayList<>();
            }
            List<ChatMessage> messages = new ArrayList<>();
            for (String json : raw) {
                ChatMessage msg = OBJECT_MAPPER.readValue(json, MSG_TYPE);
                messages.add(msg);
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取对话上下文失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 清空会话上下文
     */
    public void clear(String sessionId) {
        try {
            redisTemplate.delete(buildKey(sessionId));
        } catch (Exception e) {
            log.warn("清空对话上下文失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
