package cn.kong.zbrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百炼 SDK 运维参数配置
 *
 * <p>模型名称、API Key、Base URL 等模型选择参数已迁移到数据库 sys_llm_model 表，
 * 此处仅保留运维级别的调用参数（批量大小、超时、重试）。</p>
 *
 * @author zbrain-team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zbrain.dashscope")
public class DashScopeConfig {

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
