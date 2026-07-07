package cn.kong.zbrain.service;

import java.util.List;

/**
 * 重排序服务接口
 *
 * <p>模型配置从数据库 sys_llm_model 表读取，支持动态切换。</p>
 *
 * @author zbrain-team
 */
public interface RerankService {

    /**
     * 清除缓存的模型配置（配置变更时调用）
     */
    void clearCache();

    /**
     * 对候选文档进行重排序
     *
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param topN      返回 Top N
     * @return 重排序后的文档索引列表（按相关性从高到低）
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);

    /**
     * 重排序结果
     *
     * @param index          原文档列表中的索引
     * @param relevanceScore 相关性分数
     */
    record RerankResult(int index, double relevanceScore) {}
}
