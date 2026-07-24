package cn.kong.eon.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统提示词实体
 *
 * <p>统一管理所有硬编码的提示词，通过 prompt_key 逻辑键标识。</p>
 *
 * @author eon-team
 */
@Data
public class SysPrompt {
    private Long id;
    private String promptKey;
    private String name;
    private String description;
    private String content;
    private Boolean isActive;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
