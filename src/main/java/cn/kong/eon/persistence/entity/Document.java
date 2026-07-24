package cn.kong.eon.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体
 *
 * <p>文档状态机如下：
 * <pre>
 *   pending -> parsing -> pending_review -> embedding -> success
 *                                  |             |
 *                                  v             v
 *                               (审核驳回)     failed
 * </pre>
 * </p>
 *
 * @author eon-team
 */
@Data
public class Document {
    private Long id;
    private Long kbId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String fileHash;
    /** 分块大小（Token 数），为 null 时使用知识库默认 chunkSize */
    private Integer chunkSize;
    /** 解析类型：tika / llama_index */
    private String parseType;
    /** pending / parsing / pending_review / embedding / success / failed */
    private String status;
    private Integer chunkCount;
    /** 解析进度 0-100 */
    private Integer parseProgress;
    private String errorMessage;
    /** JSON 元数据 */
    private String metadata;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
