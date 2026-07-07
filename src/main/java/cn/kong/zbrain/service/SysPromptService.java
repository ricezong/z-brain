package cn.kong.zbrain.service;

import cn.kong.zbrain.entity.SysPrompt;

import java.util.List;

/**
 * 系统提示词服务接口
 *
 * <p>统一管理所有系统级提示词，替代硬编码。</p>
 *
 * @author zbrain-team
 */
public interface SysPromptService {

    /**
     * 根据 prompt_key 获取提示词内容
     *
     * @param promptKey 逻辑键
     * @return 提示词内容，找不到时返回 null
     */
    String getContent(String promptKey);

    /**
     * 根据 prompt_key 获取提示词实体
     *
     * @param promptKey 逻辑键
     * @return 提示词实体
     */
    SysPrompt getByKey(String promptKey);

    /**
     * 获取所有提示词列表
     */
    List<SysPrompt> list();

    /**
     * 更新提示词
     */
    void update(Long id, String name, String description, String content, Boolean isActive);

    /**
     * 根据 ID 获取
     */
    SysPrompt getById(Long id);
}
