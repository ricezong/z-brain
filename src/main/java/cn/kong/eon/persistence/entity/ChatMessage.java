package cn.kong.eon.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 会话消息实体（ChatMemory 持久化）
 *
 * <p>对应表 chat_message，由 {@code ChatMemoryRepository} 读写。
 * content 字段存储序列化 JSON（type/text/metadata）。</p>
 *
 * @author eon-team
 */
@Data
public class ChatMessage {
    private Long id;
    private String conversationId;
    /** USER / ASSISTANT / SYSTEM / TOOL */
    private String type;
    /** 消息序列化 JSON */
    private String content;
    private LocalDateTime msgTime;
}
