package cn.kong.eon.rag;

import cn.kong.eon.common.exception.BusinessException;
import cn.kong.eon.common.util.CommonUtils;
import cn.kong.eon.config.ConfigService;
import cn.kong.eon.llm.ModelType;
import cn.kong.eon.persistence.entity.SysLlmModel;
import com.alibaba.dashscope.embeddings.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 向量化服务（重写：去 DashScopeConfig/SysLlmModelService/EmbeddingCache 耦合，统一走 ConfigService）
 *
 * <p>查询向量化（检索）和文档向量化（知识库管理）共用本服务。
 * Redis 缓存层保留（文档内容不变时可复用），查询向量化不走缓存。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int MAX_API_BATCH_SIZE = 10;
    private static final TextEmbedding TEXT_EMBEDDING = new TextEmbedding();
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final ConfigService configService;
    private final StringRedisTemplate redisTemplate;

    private volatile SysLlmModel cachedModel;

    /**
     * 单条文本向量化（查询用，不走缓存）
     */
    public String embed(String text) {
        if (text == null || text.isBlank()) return null;
        List<String> results = callDashScope(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 批量文本向量化（文档用，走 Redis 缓存）
     */
    public List<String> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();

        List<String> result = new ArrayList<>(texts.size());
        Map<Integer, String> pending = new HashMap<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cached = getCache(text);
            if (cached != null) {
                result.add(i, cached);
            } else {
                result.add(i, null);
                pending.put(i, text);
            }
        }

        if (!pending.isEmpty()) {
            List<String> pendingTexts = new ArrayList<>(pending.values());
            Map<String, String> textToVector = new HashMap<>();
            List<String> vectors = callDashScope(pendingTexts);
            for (int j = 0; j < pendingTexts.size(); j++) {
                textToVector.put(pendingTexts.get(j), vectors.get(j));
            }
            for (Map.Entry<Integer, String> entry : pending.entrySet()) {
                String vector = textToVector.get(entry.getValue());
                result.set(entry.getKey(), vector);
                putCache(entry.getValue(), vector);
            }
        }
        return result;
    }

    public String toVectorString(List<Double> vector) {
        if (vector == null || vector.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        return sb.append("]").toString();
    }

    public float[] fromVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.length() < 2) return new float[0];
        String content = vectorStr.substring(1, vectorStr.length() - 1);
        if (content.isEmpty()) return new float[0];
        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    public void clearCache() {
        cachedModel = null;
    }

    // ==================== 内部方法 ====================

    private List<String> callDashScope(List<String> texts) {
        SysLlmModel model = getModel();
        List<String> allVectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_API_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_API_BATCH_SIZE, texts.size()));
            try {
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .model(model.getModelName())
                        .texts(batch)
                        .apiKey(model.getApiKey())
                        .build();
                TextEmbeddingResult result = TEXT_EMBEDDING.call(param);
                for (TextEmbeddingResultItem item : result.getOutput().getEmbeddings()) {
                    allVectors.add(toVectorString(item.getEmbedding()));
                }
            } catch (Exception e) {
                log.error("百炼向量化失败: {}", e.getMessage(), e);
                throw new BusinessException("向量化失败: " + e.getMessage(), e);
            }
        }
        return allVectors;
    }

    private SysLlmModel getModel() {
        if (cachedModel != null) return cachedModel;
        synchronized (this) {
            if (cachedModel != null) return cachedModel;
            SysLlmModel model = configService.getDefaultModel(ModelType.EMBEDDING.getCode());
            if (model == null) {
                throw new BusinessException("未找到默认 embedding 模型配置");
            }
            log.info("[EmbeddingService] 初始化: name={}, model={}", model.getName(), model.getModelName());
            cachedModel = model;
            return model;
        }
    }

    private String getCache(String text) {
        try {
            return redisTemplate.opsForValue().get("eon:emb:" + hashKey(text));
        } catch (Exception e) {
            return null;
        }
    }

    private void putCache(String text, String vector) {
        try {
            if (vector != null) {
                redisTemplate.opsForValue().set("eon:emb:" + hashKey(text), vector, CACHE_TTL);
            }
        } catch (Exception e) {
            log.debug("[EmbeddingService] 缓存写入失败: {}", e.getMessage());
        }
    }

    /** ★ 内容寻址缓存键：SHA-256 防碰撞（替代旧 hashCode） */
    private String hashKey(String text) {
        return CommonUtils.sha256(text);
    }
}
