package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板实体
 *
 * <p>user_prompt 支持 {context} 和 {question} 占位符。</p>
 *
 * @author zbrain-team
 */
@Data
public class PromptTemplate {
    private Long id;
    private Long kbId;
    private String name;
    private String systemPrompt;
    private String userPrompt;
    private Boolean isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
