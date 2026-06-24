package cn.kong.zbrain.dto.request;

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
}
