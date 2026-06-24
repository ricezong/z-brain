package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建知识库请求
 *
 * @author zbrain-team
 */
@Data
public class KnowledgeBaseCreateRequest {
    @NotBlank(message = "知识库名称不能为空")
    private String name;
    private String description;
    private String category;
    private Long promptTemplateId;
}
