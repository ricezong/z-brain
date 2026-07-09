package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.entity.SysApiConfig;
import cn.kong.zbrain.mapper.SysApiConfigMapper;
import cn.kong.zbrain.service.SysApiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部 API 配置服务实现
 *
 * <p>按 {@code configType} 管理不同服务的配置记录，首次启动时由 schema.sql 初始化默认数据。
 * 配置更新后，对应服务在下次调用时自动使用新配置。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysApiConfigServiceImpl implements SysApiConfigService {

    private final SysApiConfigMapper sysApiConfigMapper;

    @Override
    public SysApiConfig getConfig(String configType) {
        SysApiConfig config = sysApiConfigMapper.selectByType(configType);
        if (config == null) {
            log.warn("sys_api_config 表无 config_type={} 的记录，返回默认配置", configType);
            config = new SysApiConfig();
            config.setConfigType(configType);
            config.setEnabled(true);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String configType, SysApiConfig config) {
        SysApiConfig existing = getConfig(configType);
        if (existing.getId() == null) {
            // 首次配置：插入
            config.setConfigType(configType);
            sysApiConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            sysApiConfigMapper.update(config);
        }
        log.info("API 配置已更新: configType={}, enabled={}, baseUrl={}",
                configType, config.getEnabled(), config.getBaseUrl());
    }

    @Override
    public boolean isEnabled(String configType) {
        return Boolean.TRUE.equals(getConfig(configType).getEnabled());
    }
}
