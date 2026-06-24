package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话日志实体（审计）
 *
 * <p>记录问答全链路信息，包括改写 Query、HyDE 答案、命中分块、
 * 检索过程信息、Token 消耗、耗时等。</p>
 *
 * @author zbrain-team
 */
@Data
public class ChatLog {
    private Long id;
    private String sessionId;
    private Long kbId;
    private String userId;
    private String query;
    private String rewrittenQuery;
    private String hydeAnswer;
    private String answer;
    /** JSON：命中的分块 ID 列表 */
    private String hitChunkIds;
    /** JSON：检索过程信息 */
    private String retrievalInfo;
    /** JSON：Token 消耗统计 */
    private String tokenUsage;
    private Long costTimeMs;
    private LocalDateTime createTime;
}
