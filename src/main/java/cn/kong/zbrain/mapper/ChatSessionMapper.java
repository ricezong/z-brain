package cn.kong.zbrain.mapper;

import cn.kong.zbrain.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话会话 Mapper
 *
 * @author zbrain-team
 */
@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    int update(ChatSession session);

    int deleteById(@Param("id") String id);

    ChatSession selectById(@Param("id") String id);

    List<ChatSession> selectByKbId(@Param("kbId") Long kbId, @Param("offset") int offset, @Param("limit") int limit);

    long countByKbId(@Param("kbId") Long kbId);

    int incrementMessageCount(@Param("id") String id);
}
