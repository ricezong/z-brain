package cn.kong.zbrain.entity;

import lombok.Data;

/**
 * 外部 API 配置实体
 *
 * <p>通用配置表，按 {@code configType} 区分不同外部服务（如 llama_index、其他第三方 API 等）。
 * 通用字段（enabled / apiKey / baseUrl）独立存储，各服务专属参数放在 {@code config} JSONB 字段中。</p>
 *
 * @author zbrain-team
 */
@Data
public class SysApiConfig {
    private Long id;
    /** 配置类型标识，如 llama_index / 其他第三方服务 */
    private String configType;
    /** 是否启用 */
    private Boolean enabled;
    /** API Key */
    private String apiKey;
    /** API Base URL */
    private String baseUrl;
    /** 专属配置（JSON 字符串），如 {"tier":"AGENTIC"} */
    private String config;
}
