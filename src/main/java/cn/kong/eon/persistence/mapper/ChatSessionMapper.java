package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 瀵硅瘽浼氳瘽 Mapper
 *
 * @author eon-team
 */
@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    int update(ChatSession session);

    int deleteById(@Param("id") String id);

    ChatSession selectById(@Param("id") String id);

    List<ChatSession> selectByKbId(@Param("kbId") Long kbId, @Param("offset") int offset, @Param("limit") int limit);

    long countByKbId(@Param("kbId") Long kbId);

    List<ChatSession> selectByUserId(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

    int incrementMessageCount(@Param("id") String id);
}

