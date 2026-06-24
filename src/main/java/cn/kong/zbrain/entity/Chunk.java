package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分块实体（系统核心表）
 *
 * <p>存储向量、全文检索向量、元数据及父子关系。</p>
 * <ul>
 *   <li>仅子块向量化，父块 contentVector 为 null</li>
 *   <li>tsv 由 PG 触发器自动维护</li>
 *   <li>metadata 存储页码、坐标、字符偏移量等</li>
 * </ul>
 *
 * @author zbrain-team
 */
@Data
public class Chunk {
    private Long id;
    private Long docId;
    private Long kbId;
    /** 父块 ID，子块才有 */
    private Long parentId;
    /** parent / child */
    private String chunkType;
    private String content;
    /** 向量字段，字符串形式（如 "[0.1,0.2,...]"），仅子块向量化 */
    private String contentVector;
    /** 全文检索向量，由触发器维护，查询时不直接使用 */
    private String tsv;
    private Integer tokenCount;
    /** draft / active */
    private String status;
    /** JSON 字符串：page、coordinates、start_index、end_index 等 */
    private String metadata;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
