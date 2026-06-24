package cn.kong.zbrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百炼 SDK 配置
 *
 * <p>用于调用阿里云百炼平台的文本向量模型 (text-embedding-v4)
 * 与重排序模型 (qwen3-rerank)。</p>
 *
 * @author zbrain-team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zbrain.dashscope")
public class DashScopeConfig {

    /**
     * 百炼 API 基础 URL
     */
    private String baseUrl;

    /**
     * 百炼 API Key
     */
    private String apiKey;

    /**
     * 文本向量模型名称
     */
    private String embeddingModel = "text-embedding-v4";

    /**
     * 重排序模型名称
     */
    private String rerankModel = "qwen3-rerank";

    /**
     * 批量向量化每批最大条数（百炼 text-embedding-v4 API 限制单次最多 10 条）
     */
    private int embeddingBatchSize = 10;

    /**
     * 调用超时时间（毫秒）
     */
    private long timeout = 60000L;

    /**
     * 失败重试次数
     */
    private int retry = 3;

}
