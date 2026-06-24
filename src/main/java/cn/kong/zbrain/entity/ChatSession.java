package cn.kong.zbrain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体
 *
 * @author zbrain-team
 */
@Data
public class ChatSession {
    /** UUID */
    private String id;
    private Long kbId;
    private String title;
    private String userId;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
