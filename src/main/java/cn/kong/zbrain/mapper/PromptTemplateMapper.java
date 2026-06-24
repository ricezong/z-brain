package cn.kong.zbrain.mapper;

import cn.kong.zbrain.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提示词模板 Mapper
 *
 * @author zbrain-team
 */
@Mapper
public interface PromptTemplateMapper {

    int insert(PromptTemplate template);

    int update(PromptTemplate template);

    int deleteById(@Param("id") Long id);

    PromptTemplate selectById(@Param("id") Long id);

    PromptTemplate selectByKbId(@Param("kbId") Long kbId);

    PromptTemplate selectDefault();

    List<PromptTemplate> selectList(@Param("kbId") Long kbId);
}
