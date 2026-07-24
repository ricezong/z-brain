package cn.kong.eon.agent.orchestrator;

import lombok.Data;

/**
 * Agent 对话请求
 *
 * @author eon-team
 */
@Data
public class AgentChatRequest {

    /** 会话 ID，为空时自动创建新会话（agent 模式唯一入口） */
    private String sessionId;

    /** 用户消息 */
    private String message;

    /** 知识库 ID，为空时跳过知识库检索（knowledge_search 工具按需调用） */
    private Long kbId;

    /** 用户标识 */
    private String userId;
}
