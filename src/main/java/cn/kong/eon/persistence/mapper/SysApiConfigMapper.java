package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.SysApiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 外部 API 配置 Mapper
 *
 * <p>按 {@code configType} 管理不同服务的配置记录，提供按类型查询 / 插入 / 更新操作。</p>
 *
 * @author eon-team
 */
@Mapper
public interface SysApiConfigMapper {

    /** 查询全部配置 */
    List<SysApiConfig> selectAll();

    /** 按配置类型查询单条记录 */
    SysApiConfig selectByType(@Param("configType") String configType);

    /** 插入配置 */
    int insert(SysApiConfig config);

    /** 更新配置 */
    int update(SysApiConfig config);
}
