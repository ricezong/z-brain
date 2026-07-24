package cn.kong.eon.persistence.entity;

import lombok.Data;

/**
 * 外部 API 配置实体
 *
 * <p>按 {@code configType} 管理不同服务的配置记录，如 llama_index 等。
 * 每条记录包含 enabled / apiKey / baseUrl 以及可选的 {@code config} JSONB 扩展字段。</p>
 *
 * @author eon-team
 */
@Data
public class SysApiConfig {
    private Long id;
    /** 配置类型唯一标识（如 llama_index / 其他服务类型） */
    private String configType;
    /** 是否启用 */
    private Boolean enabled;
    /** API Key */
    private String apiKey;
    /** API Base URL */
    private String baseUrl;
    /** 扩展配置 JSON 字符串，如 {"tier":"AGENTIC"} */
    private String config;
}
