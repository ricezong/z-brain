package cn.kong.eon.rag.retrieval;

import cn.kong.eon.common.exception.BusinessException;
import cn.kong.eon.config.ConfigService;
import cn.kong.eon.llm.ModelType;
import cn.kong.eon.persistence.entity.SysLlmModel;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 重排器（重写：5s 超时快速失败 + 降级 RRF，去 Thread.sleep 重试）
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Reranker {

    private static final TextReRank TEXT_RERANK = new TextReRank();
    private static final long TIMEOUT_MS = 5000;

    private final ConfigService configService;
    private volatile SysLlmModel cachedRerankModel;

    /**
     * 重排（5s 超时强制失败，失败/超时统一降级 RRF 顺序）
     *
     * @param query     查询
     * @param documents 候选文档列表
     * @param topN      返回 Top N
     * @return 重排结果（index + relevanceScore）
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            // ★ CompletableFuture + get(timeout) 强制 5s 超时，超时/异常统一降级 RRF 顺序
            Future<List<RerankResult>> future = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "rerank-call");
                t.setDaemon(true);
                return t;
            }).submit(() -> callOnce(query, documents, topN));
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[Reranker] 调用超时({}ms)，降级返回原始顺序", TIMEOUT_MS);
            return fallbackOrder(documents, topN);
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[Reranker] 调用失败，降级返回原始顺序: {}", e.getMessage());
            return fallbackOrder(documents, topN);
        }
    }

    /**
     * 降级：返回原始顺序的前 topN 个（RRF 名次）
     */
    private List<RerankResult> fallbackOrder(List<String> documents, int topN) {
        List<RerankResult> fallback = new ArrayList<>();
        int limit = Math.min(topN, documents.size());
        for (int i = 0; i < limit; i++) {
            fallback.add(new RerankResult(i, 1.0 / (i + 1)));
        }
        return fallback;
    }

    private List<RerankResult> callOnce(String query, List<String> documents, int topN) {
        try {
            SysLlmModel model = getRerankModel();
            TextReRankParam param = TextReRankParam.builder()
                    .model(model.getModelName())
                    .query(query)
                    .documents(documents)
                    .topN(Math.min(topN, documents.size()))
                    .returnDocuments(false)
                    .apiKey(model.getApiKey())
                    .build();

            log.info("[Reranker] 调用: model={}, docCount={}, topN={}", model.getModelName(), documents.size(), topN);
            TextReRankResult result = TEXT_RERANK.call(param);

            List<RerankResult> results = new ArrayList<>();
            for (TextReRankOutput.Result item : result.getOutput().getResults()) {
                results.add(new RerankResult(item.getIndex(), item.getRelevanceScore()));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Rerank 调用失败: " + e.getMessage(), e);
        }
    }

    private SysLlmModel getRerankModel() {
        if (cachedRerankModel != null) {
            return cachedRerankModel;
        }
        synchronized (this) {
            if (cachedRerankModel != null) {
                return cachedRerankModel;
            }
            SysLlmModel model = configService.getDefaultModel(ModelType.RERANK.getCode());
            if (model == null) {
                throw new BusinessException("未找到默认 rerank 模型配置");
            }
            log.info("[Reranker] 初始化: name={}, model={}", model.getName(), model.getModelName());
            cachedRerankModel = model;
            return model;
        }
    }

    public void clearCache() {
        cachedRerankModel = null;
    }

    public record RerankResult(int index, double relevanceScore) {}
}
