package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分块拆分请求
 *
 * @author zbrain-team
 */
@Data
public class ChunkSplitRequest {
    @NotNull(message = "分块 ID 不能为空")
    private Long chunkId;
    /** 拆分位置（字符偏移量） */
    @NotNull(message = "拆分位置不能为空")
    private Integer splitPosition;
}
