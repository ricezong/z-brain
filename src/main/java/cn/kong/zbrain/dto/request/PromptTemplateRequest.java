package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提示词模板请求
 *
 * @author zbrain-team
 */
@Data
public class PromptTemplateRequest {
    private Long kbId;
    @NotBlank(message = "模板名称不能为空")
    private String name;
    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;
    @NotBlank(message = "用户提示词不能为空")
    private String userPrompt;
    private Boolean isDefault;
}
