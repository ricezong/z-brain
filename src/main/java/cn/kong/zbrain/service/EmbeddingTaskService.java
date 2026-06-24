package cn.kong.zbrain.service;

/**
 * 向量化任务服务接口
 *
 * <p>负责将审核通过的分块批量向量化并写入 PG。</p>
 *
 * @author zbrain-team
 */
public interface EmbeddingTaskService {

    /**
     * 异步执行向量化任务
     *
     * @param documentId 文档 ID
     */
    void embedAsync(Long documentId);

    /**
     * 同步执行向量化任务
     *
     * @param documentId 文档 ID
     */
    void embed(Long documentId);
}
