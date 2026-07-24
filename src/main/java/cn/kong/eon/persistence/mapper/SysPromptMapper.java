package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.SysPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统提示词 Mapper
 *
 * @author eon-team
 */
@Mapper
public interface SysPromptMapper {

    int insert(SysPrompt prompt);

    int update(SysPrompt prompt);

    int deleteById(@Param("id") Long id);

    SysPrompt selectById(@Param("id") Long id);

    SysPrompt selectByKey(@Param("promptKey") String promptKey);

    List<SysPrompt> selectList();
}
