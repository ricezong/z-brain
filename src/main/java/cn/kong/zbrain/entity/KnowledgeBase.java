package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体
 *
 * @author zbrain-team
 */
@Data
public class KnowledgeBase {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Long promptTemplateId;
    private String status;
    private Integer docCount;
    private Integer chunkCount;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
