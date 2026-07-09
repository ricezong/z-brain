package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /** 子块分块大小（Token 数），必填，默认 300 */
    @NotNull(message = "分块大小不能为空")
    @Min(value = 64, message = "分块大小不能小于 64")
    private Integer chunkSize = 300;
}
