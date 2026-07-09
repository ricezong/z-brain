package cn.kong.zbrain.service;

import cn.kong.zbrain.entity.SysApiConfig;

import java.util.List;

/**
 * 外部 API 配置服务接口
 *
 * <p>按 {@code configType} 管理不同外部服务的连接配置，支持在线修改。
 * 配置更新后，对应服务在下次调用时自动使用新配置，无需重启。</p>
 *
 * @author zbrain-team
 */
public interface SysApiConfigService {

    /**
     * 获取全部配置
     *
     * @return 配置列表
     */
    List<SysApiConfig> listAll();

    /**
     * 获取指定类型的配置
     *
     * @param configType 配置类型标识（如 llama_index）
     * @return 配置实体（不为 null）
     */
    SysApiConfig getConfig(String configType);

    /**
     * 更新指定类型的配置
     *
     * @param configType 配置类型标识
     * @param config     新配置
     */
    void updateConfig(String configType, SysApiConfig config);

    /**
     * 判断指定类型是否已启用
     *
     * @param configType 配置类型标识
     * @return true 表示已启用
     */
    boolean isEnabled(String configType);
}
