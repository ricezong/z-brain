package cn.kong.zbrain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求
 *
 * @author zbrain-team
 */
@Data
public class ChatRequest {
    /** 会话 ID，为空则新建会话 */
    private String sessionId;

    @NotBlank(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotBlank(message = "问题不能为空")
    private String query;

    /** 用户 ID */
    private String userId;

    /** 是否流式输出 */
    private Boolean stream = true;

    /** 是否启用 HyDE 增强 */
    private Boolean enableHyde = true;

    /** 是否启用 Query 改写 */
    private Boolean enableQueryRewrite = true;
}
