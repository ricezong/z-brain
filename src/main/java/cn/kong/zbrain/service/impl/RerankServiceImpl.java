package cn.kong.zbrain.service.impl;

import com.alibaba.dashscope.rerank.TextReRank;
import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.config.DashScopeConfig;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.enums.ModelType;
import cn.kong.zbrain.service.RerankService;
import cn.kong.zbrain.service.SysLlmModelService;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 重排序服务实现（基于百炼 SDK + 数据库模型配置）
 *
 * <p>对 RRF 融合后的 Top 10 子块内容进行语义相关性精排，
 * 根据返回的相关性分数剔除冗余，提取最终 Top 5 子块。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankServiceImpl implements RerankService {

    /** 百炼 SDK 重排序客户端（无状态，安全复用） */
    private static final TextReRank TEXT_RERANK = new TextReRank();

    private final DashScopeConfig dashScopeConfig;
    private final SysLlmModelService sysLlmModelService;

    /** 缓存的 rerank 模型配置，配置变更后通过 clearCache() 失效 */
    private volatile SysLlmModel cachedRerankModel;

    private static final String INSTRUCT =
            "Given a web search query, retrieve relevant passages that answer the query.";

    /**
     * 获取默认 rerank 模型配置（带缓存）
     */
    private SysLlmModel getRerankModel() {
        if (cachedRerankModel != null) {
            return cachedRerankModel;
        }
        synchronized (this) {
            if (cachedRerankModel != null) {
                return cachedRerankModel;
            }
            SysLlmModel model = sysLlmModelService.getDefaultByType(ModelType.RERANK.getCode());
            if (model == null) {
                throw new BusinessException("未找到默认的 rerank 模型配置，请在系统配置中添加");
            }
            log.info("初始化 Rerank 模型配置: name={}, model={}", model.getName(), model.getModelName());
            cachedRerankModel = model;
            return model;
        }
    }

    @Override
    public void clearCache() {
        synchronized (this) {
            cachedRerankModel = null;
        }
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        int retry = 0;
        while (retry <= dashScopeConfig.getRetry()) {
            try {
                return callOnce(query, documents, topN);
            } catch (Exception e) {
                retry++;
                if (retry > dashScopeConfig.getRetry()) {
                    log.error("百炼 Rerank 调用失败，已达最大重试次数: {}", e.getMessage(), e);
                    throw new BusinessException("百炼 Rerank 调用失败: " + e.getMessage(), e);
                }
                log.warn("百炼 Rerank 调用失败，第 {} 次重试: {}", retry, e.getMessage());
                try {
                    Thread.sleep(1000L * retry);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("Rerank 线程被中断", ie);
                }
            }
        }
        return new ArrayList<>();
    }

    private List<RerankResult> callOnce(String query, List<String> documents, int topN) throws Exception {
        SysLlmModel model = getRerankModel();
        TextReRankParam param = TextReRankParam.builder()
                .model(model.getModelName())
                .query(query)
                .documents(documents)
                .topN(Math.min(topN, documents.size()))
                .returnDocuments(false)
                .apiKey(model.getApiKey())
                .build();

        log.info("调用百炼 Rerank API: model={}, docCount={}, topN={}",
                model.getModelName(), documents.size(), topN);
        TextReRankResult result = TEXT_RERANK.call(param);
        log.info("百炼 Rerank API 返回: resultCount={}", result.getOutput().getResults().size());

        List<RerankResult> results = new ArrayList<>();
        for (TextReRankOutput.Result item : result.getOutput().getResults()) {
            results.add(new RerankResult(item.getIndex(), item.getRelevanceScore()));
        }
        return results;
    }
}
