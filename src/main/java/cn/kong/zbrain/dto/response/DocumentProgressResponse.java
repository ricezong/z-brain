package cn.kong.zbrain.dto.response;

import lombok.Data;

/**
 * 文档处理进度响应
 *
 * @author zbrain-team
 */
@Data
public class DocumentProgressResponse {
    private Long documentId;
    private String status;
    private Integer progress;
    private String errorMessage;
    private Integer chunkCount;
}
