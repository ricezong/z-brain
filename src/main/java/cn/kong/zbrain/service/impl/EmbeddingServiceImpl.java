package cn.kong.zbrain.service.impl;

import com.alibaba.dashscope.embeddings.*;
import cn.kong.zbrain.cache.EmbeddingCache;
import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.config.DashScopeConfig;
import cn.kong.zbrain.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化服务实现（基于百炼 SDK）
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>先检查 Redis Embedding 缓存，命中直接返回</li>
 *   <li>未命中的文本批量调用百炼 SDK text-embedding-v4</li>
 *   <li>SDK 返回向量后写入 Redis 缓存</li>
 *   <li>按顺序返回向量列表</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    /** 百炼 text-embedding API 单次请求最大文本条数（硬限制） */
    private static final int MAX_API_BATCH_SIZE = 10;

    private final DashScopeConfig dashScopeConfig;
    private final EmbeddingCache embeddingCache;

    @Override
    public String embed(String text) {
        List<String> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<String> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>(texts.size());
        // 1. 先查缓存
        Map<String, String> cacheHits = embeddingCache.batchGet(texts);
        // 占位：缓存命中的位置直接填入，未命中的收集起来批量调用
        Map<Integer, String> pendingIndexToText = new HashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cached = cacheHits.get(text);
            if (cached != null) {
                result.add(i, cached);
            } else {
                result.add(i, null);
                pendingIndexToText.put(i, text);
            }
        }

        if (pendingIndexToText.isEmpty()) {
            log.debug("全部命中 Embedding 缓存，size={}", texts.size());
            return result;
        }

        // 2. 未命中的批量调用 SDK
        List<String> pendingTexts = new ArrayList<>(pendingIndexToText.values());
        Map<String, String> textToVector = callDashScopeBatch(pendingTexts);

        // 3. 写入缓存并回填结果
        embeddingCache.batchPut(textToVector);
        for (Map.Entry<Integer, String> entry : pendingIndexToText.entrySet()) {
            String vector = textToVector.get(entry.getValue());
            result.set(entry.getKey(), vector);
        }

        return result;
    }

    /**
     * 调用百炼 SDK 批量向量化（内置分批与重试）
     */
    private Map<String, String> callDashScopeBatch(List<String> texts) {
        Map<String, String> textToVector = new HashMap<>();
        int batchSize = Math.min(dashScopeConfig.getEmbeddingBatchSize(), MAX_API_BATCH_SIZE);

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            int retry = 0;
            while (retry <= dashScopeConfig.getRetry()) {
                try {
                    List<String> vectors = callOnce(batch);
                    for (int j = 0; j < batch.size(); j++) {
                        textToVector.put(batch.get(j), vectors.get(j));
                    }
                    break;
                } catch (Exception e) {
                    retry++;
                    if (retry > dashScopeConfig.getRetry()) {
                        log.error("百炼向量化调用失败，已达最大重试次数: {}", e.getMessage(), e);
                        throw new BusinessException("百炼向量化调用失败: " + e.getMessage(), e);
                    }
                    log.warn("百炼向量化调用失败，第 {} 次重试: {}", retry, e.getMessage());
                    try {
                        Thread.sleep(1000L * retry);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException("向量化线程被中断", ie);
                    }
                }
            }
        }
        return textToVector;
    }

    /**
     * 单次调用百炼 SDK
     */
    private List<String> callOnce(List<String> texts) throws Exception {
        TextEmbeddingParam param = TextEmbeddingParam.builder()
                .model(dashScopeConfig.getEmbeddingModel())
                .texts(texts)
                .apiKey(dashScopeConfig.getApiKey())
                .build();

        TextEmbedding textEmbedding = new TextEmbedding();
        TextEmbeddingResult result = textEmbedding.call(param);

        List<String> vectors = new ArrayList<>();
        for (TextEmbeddingResultItem item : result.getOutput().getEmbeddings()) {
            vectors.add(toVectorString(item.getEmbedding()));
        }
        return vectors;
    }

    @Override
    public String toVectorString(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public float[] fromVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.length() < 2) {
            return new float[0];
        }
        String content = vectorStr.substring(1, vectorStr.length() - 1);
        if (content.isEmpty()) {
            return new float[0];
        }
        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

}
