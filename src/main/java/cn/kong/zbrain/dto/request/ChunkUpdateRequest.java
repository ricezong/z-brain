package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分块更新请求（人工审核工作台）
 *
 * @author zbrain-team
 */
@Data
public class ChunkUpdateRequest {
    @NotNull(message = "分块 ID 不能为空")
    private Long id;
    /** 修改后的内容 */
    private String content;
    /** 调整父块 ID */
    private Long parentId;
    /** 状态：draft / active */
    private String status;
    /** 元数据 JSON */
    private String metadata;
}
