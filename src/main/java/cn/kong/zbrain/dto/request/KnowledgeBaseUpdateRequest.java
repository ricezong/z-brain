package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 更新知识库请求
 *
 * @author zbrain-team
 */
@Data
public class KnowledgeBaseUpdateRequest {
    private String name;
    private String description;
    private String category;
    private Long promptTemplateId;
    private String status;

    /** 分块大小（Token 数），更新时可选 */
    @Min(value = 64, message = "分块大小不能小于 64")
    private Integer chunkSize;
}
