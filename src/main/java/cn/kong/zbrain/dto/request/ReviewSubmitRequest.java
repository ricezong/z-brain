package cn.kong.zbrain.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 审核提交请求（批量 Diff）
 *
 * @author zbrain-team
 */
@Data
public class ReviewSubmitRequest {
    /** 新增的分块 */
    private List<ChunkDiffItem> added;
    /** 修改的分块 */
    private List<ChunkDiffItem> modified;
    /** 删除的分块 ID */
    private List<Long> deleted;

    @Data
    public static class ChunkDiffItem {
        private Long id;
        private Long docId;
        private Long kbId;
        private Long parentId;
        private String chunkType;
        private String content;
        private Integer tokenCount;
        private String metadata;
    }
}
