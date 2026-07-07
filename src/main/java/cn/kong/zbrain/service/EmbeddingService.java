package cn.kong.zbrain.service;

import java.util.List;

/**
 * 向量化服务接口
 *
 * <p>模型配置从数据库 sys_llm_model 表读取，支持动态切换。</p>
 *
 * @author zbrain-team
 */
public interface EmbeddingService {

    /**
     * 清除缓存的模型配置（配置变更时调用）
     */
    void clearCache();

    /**
     * 单条文本向量化
     *
     * @param text 输入文本
     * @return 向量字符串 "[0.1,0.2,...]"
     */
    String embed(String text);

    /**
     * 批量文本向量化
     *
     * <p>优先检查 Redis 缓存，缺失才调用百炼 SDK，节省成本。</p>
     *
     * @param texts 文本列表
     * @return 文本到向量的映射
     */
    List<String> embedBatch(List<String> texts);

    /**
     * 将向量集合转为 PG vector 字符串格式
     */
    String toVectorString(List<Double> vector);

    /**
     * 将 PG vector 字符串转为 float 数组
     */
    float[] fromVectorString(String vectorStr);
}
