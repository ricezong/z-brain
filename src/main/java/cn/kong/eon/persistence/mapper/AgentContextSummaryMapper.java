package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.AgentContextSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 上下文压缩增量摘要 Mapper
 *
 * @author eon-team
 */
@Mapper
public interface AgentContextSummaryMapper {

    /** 插入摘要（COMPACT 阶段写入） */
    int insert(AgentContextSummary summary);

    /** 查询会话最新摘要（作为下次合并的基准） */
    AgentContextSummary selectLatestBySessionId(@Param("sessionId") String sessionId);
}
