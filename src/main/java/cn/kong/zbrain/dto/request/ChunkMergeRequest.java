package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 分块合并请求
 *
 * @author zbrain-team
 */
@Data
public class ChunkMergeRequest {
    @NotEmpty(message = "待合并分块 ID 列表不能为空")
    private List<Long> chunkIds;
    /** 合并后所属父块 ID */
    private Long parentId;
}
