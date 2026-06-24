package cn.kong.zbrain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 检索结果响应
 *
 * @author zbrain-team
 */
@Data
public class RetrievalResult {
    /** 命中的子块 ID */
    private Long chunkId;
    /** 父块 ID */
    private Long parentId;
    /** 子块内容 */
    private String content;
    /** 父块内容（上下文） */
    private String parentContent;
    /** 综合得分 */
    private Double score;
    /** Rerank 后的排名 */
    private Integer rerankRank;
    /** 来源文档 ID */
    private Long docId;
    /** 元数据 */
    private String metadata;
    /** 引用编号（doc_1, doc_2...） */
    private String citationLabel;
    /** 各路召回来源 */
    private List<String> sources;
}
