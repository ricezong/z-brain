package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Query 改写请求
 */
@Data
public class RewriteRequest {
    @NotBlank(message = "问题不能为空")
    private String query;

    /** 会话 ID（可选，用于多轮对话改写） */
    private String sessionId;
}
