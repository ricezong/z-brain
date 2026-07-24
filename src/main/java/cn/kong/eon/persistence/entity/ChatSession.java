package cn.kong.eon.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体
 *
 * @author eon-team
 */
@Data
public class ChatSession {
    /** UUID */
    private String id;
    private Long kbId;
    private String title;
    private String userId;
    private Integer messageCount;
    /** 会话模式：agent-个人助手（新工程唯一模式） */
    private String mode;
    /** 当前进行中的任务 ID（断点恢复入口） */
    private String currentTaskId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
