package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 会话消息 Mapper（ChatMemory 持久化）
 *
 * @author eon-team
 */
@Mapper
public interface ChatMessageMapper {

    /** 批量插入消息 */
    int batchInsert(@Param("list") List<ChatMessage> messages);

    /** 按会话查询全部消息（按 id 升序） */
    List<ChatMessage> selectByConversationId(@Param("conversationId") String conversationId);

    /** 删除会话全部消息 */
    int deleteByConversationId(@Param("conversationId") String conversationId);

    /** 按 id 更新消息内容（压缩定向更新，保留 id/msg_seq 稳定） */
    int updateContentById(@Param("id") Long id, @Param("content") String content);

    /** 批量按 id 删除（diff-based saveAll 删除多余消息） */
    int deleteByIds(@Param("ids") java.util.Collection<Long> ids);

    /** 查询所有会话 ID（去重） */
    List<String> selectDistinctConversationIds();
}
