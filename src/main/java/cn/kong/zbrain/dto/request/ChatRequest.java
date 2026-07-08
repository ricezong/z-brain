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

    /** 知识库 ID，为空则全局检索 */
    private Long kbId;

    @NotBlank(message = "问题不能为空")
    private String query;

    /** 用户 ID */
    private String userId;

    /** 对话模式：auto(默认) / chitchat / rag */
    private String mode = "auto";

    /** 指定模型 ID，为空则使用默认 chat 模型 */
    private Long modelId;

    /** 是否启用深度思考模式 */
    private Boolean thinking = false;
}
