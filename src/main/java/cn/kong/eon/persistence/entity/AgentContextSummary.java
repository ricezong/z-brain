package cn.kong.eon.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上下文压缩增量摘要链实体
 *
 * <p>对应表 agent_context_summary，COMPACT 阶段将上次摘要 + 新增对话合并为新摘要，
 * 防止"摘要的摘要"语义漂移。保留具体值（路径/ID/错误信息），删除客套话与重复表述。</p>
 *
 * @author eon-team
 */
@Data
public class AgentContextSummary {

    private Long id;
    private String sessionId;
    /** 增量摘要内容（上次摘要 + delta 合并后的新摘要） */
    private String summary;
    /** 基准消息 ID（对应 chat_message 的 msg_seq，保持单调递增） */
    private Long baseMessageId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
