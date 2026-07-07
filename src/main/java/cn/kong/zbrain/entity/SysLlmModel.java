package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 模型配置实体
 *
 * <p>参考 Dify / FastGPT / MaxKB 的模型管理方案，
 * 支持多模型配置，按 model_type 区分用途（chat / embedding / rerank）。</p>
 *
 * @author zbrain-team
 */
@Data
public class SysLlmModel {
    private Long id;
    private String name;
    /** 模型类型：chat / embedding / rerank */
    private String modelType;
    /** 提供商标识：openai_compatible / dashscope / ollama */
    private String provider;
    /** 模型标识，如 deepseek-v4-pro */
    private String modelName;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Integer maxTokens;
    /** 是否为该 model_type 的默认模型 */
    private Boolean isDefault;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
