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
    /** 子块分块大小（Token 数），默认 300 */
    private Integer chunkSize;
    private String status;
    private Integer docCount;
    private Integer chunkCount;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
