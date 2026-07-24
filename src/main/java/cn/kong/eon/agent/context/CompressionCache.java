package cn.kong.eon.agent.context;

import cn.kong.eon.config.EonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 压缩决策缓存（Redis）
 *
 * <p>两类缓存：</p>
 * <ul>
 *   <li><b>usage</b>（per session）：上一轮 LLM 返回的真实 promptTokens + totalTokens，
 *       供下轮 before 校准 JTokkit 估算（方案铁律 #3：触发优先用真实 usage）。
 *       Key：{@code eon:compress:usage:{sessionId}}，值 {@code "prompt:total"}，TTL 30min。</li>
 *   <li><b>decision</b>（per message）：某条消息已压缩到的层级（SNIP/PRUNE/COMPACT），
 *       单调度推进，保 Prompt Cache 前缀稳定（方案铁律 #1，避免坑 #4）。
 *       Key：{@code eon:compress:decision:{sessionId}:{msgSeq}}，值 tier，TTL 30min。</li>
 * </ul>
 *
 * <p>COMPACT 摘要不缓存于 Redis，持久化到 {@code agent_context_summary} 表。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompressionCache {

    private final StringRedisTemplate redisTemplate;
    private final EonProperties properties;

    private static final String USAGE_KEY_PREFIX = "compress:usage:";
    private static final String DECISION_KEY_PREFIX = "compress:decision:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private String usageKey(String sessionId) {
        return properties.getCache().getPrefix() + USAGE_KEY_PREFIX + sessionId;
    }

    private String decisionKey(String sessionId, long msgSeq) {
        return properties.getCache().getPrefix() + DECISION_KEY_PREFIX + sessionId + ":" + msgSeq;
    }

    // ==================== usage（per session）====================

    /**
     * 记录上一轮真实 usage（after 阶段调用）
     */
    public void saveUsage(String sessionId, int promptTokens, int totalTokens) {
        try {
            String val = promptTokens + ":" + totalTokens;
            redisTemplate.opsForValue().set(usageKey(sessionId), val, TTL);
        } catch (Exception e) {
            log.warn("[CompressionCache] 写入 usage 失败: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    /**
     * 读取上一轮真实 usage（before 阶段调用）
     *
     * @return [promptTokens, totalTokens]；无记录返回 null（降级用 JTokkit 估算）
     */
    public int[] getUsage(String sessionId) {
        try {
            String val = redisTemplate.opsForValue().get(usageKey(sessionId));
            if (val == null || val.isBlank() || !val.contains(":")) {
                return null;
            }
            int idx = val.indexOf(':');
            return new int[]{
                    Integer.parseInt(val.substring(0, idx)),
                    Integer.parseInt(val.substring(idx + 1))
            };
        } catch (Exception e) {
            log.warn("[CompressionCache] 读取 usage 失败: sessionId={}, err={}", sessionId, e.getMessage());
            return null;
        }
    }

    // ==================== decision（per message，单调度推进）====================

    /**
     * 标记某条消息已压缩到指定层级
     */
    public void saveDecision(String sessionId, long msgSeq, CompressionTier tier) {
        try {
            redisTemplate.opsForValue().set(decisionKey(sessionId, msgSeq), tier.name(), TTL);
        } catch (Exception e) {
            log.warn("[CompressionCache] 写入决策失败: {}:{}, err={}", sessionId, msgSeq, e.getMessage());
        }
    }

    /**
     * 查询某条消息已压缩到的层级
     *
     * @return 层级；无记录返回 null（未压缩过）
     */
    public CompressionTier getDecision(String sessionId, long msgSeq) {
        try {
            String val = redisTemplate.opsForValue().get(decisionKey(sessionId, msgSeq));
            if (val == null || val.isBlank()) {
                return null;
            }
            return CompressionTier.valueOf(val);
        } catch (Exception e) {
            log.warn("[CompressionCache] 读取决策失败: {}:{}, err={}", sessionId, msgSeq, e.getMessage());
            return null;
        }
    }

    // ==================== 清理 ====================

    /**
     * 清理会话压缩状态（usage + 所有 decision）
     */
    public void clear(String sessionId) {
        try {
            redisTemplate.delete(usageKey(sessionId));
            // 扫描清理该会话所有 decision key
            String pattern = properties.getCache().getPrefix() + DECISION_KEY_PREFIX + sessionId + ":*";
            redisTemplate.keys(pattern).forEach(redisTemplate::delete);
        } catch (Exception e) {
            log.warn("[CompressionCache] 清理失败: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }
}
