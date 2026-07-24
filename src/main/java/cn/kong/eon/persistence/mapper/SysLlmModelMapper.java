package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.SysLlmModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LLM 模型配置 Mapper
 *
 * @author eon-team
 */
@Mapper
public interface SysLlmModelMapper {

    int insert(SysLlmModel model);

    int update(SysLlmModel model);

    int deleteById(@Param("id") Long id);

    SysLlmModel selectById(@Param("id") Long id);

    SysLlmModel selectDefaultByType(@Param("modelType") String modelType);

    List<SysLlmModel> selectByType(@Param("modelType") String modelType);

    List<SysLlmModel> selectAll();

    int clearDefaultByType(@Param("modelType") String modelType);
}
