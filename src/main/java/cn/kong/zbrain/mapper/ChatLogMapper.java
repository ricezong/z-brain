package cn.kong.zbrain.mapper;

import cn.kong.zbrain.entity.ChatLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话日志 Mapper
 *
 * @author zbrain-team
 */
@Mapper
public interface ChatLogMapper {

    int insert(ChatLog log);

    ChatLog selectById(@Param("id") Long id);

    List<ChatLog> selectBySessionId(@Param("sessionId") String sessionId);

    List<ChatLog> selectByKbId(@Param("kbId") Long kbId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    long countByKbId(@Param("kbId") Long kbId);
}
