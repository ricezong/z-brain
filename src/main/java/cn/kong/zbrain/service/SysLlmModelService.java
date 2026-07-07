package cn.kong.zbrain.service;

import cn.kong.zbrain.entity.SysLlmModel;

import java.util.List;

/**
 * LLM 模型配置服务接口
 *
 * <p>参考 Dify / FastGPT / MaxKB 模型管理方案，
 * 支持多模型配置管理。</p>
 *
 * @author zbrain-team
 */
public interface SysLlmModelService {

    /**
     * 创建模型配置
     */
    Long create(SysLlmModel model);

    /**
     * 更新模型配置
     */
    void update(SysLlmModel model);

    /**
     * 删除模型配置
     */
    void delete(Long id);

    /**
     * 根据 ID 获取
     */
    SysLlmModel getById(Long id);

    /**
     * 获取所有模型配置
     */
    List<SysLlmModel> listAll();

    /**
     * 按类型获取模型列表
     */
    List<SysLlmModel> listByType(String modelType);

    /**
     * 获取指定类型的默认模型配置
     *
     * @param modelType 模型类型：chat / embedding / rerank
     * @return 默认模型配置，找不到返回 null
     */
    SysLlmModel getDefaultByType(String modelType);

    /**
     * 设置默认模型
     */
    void setDefault(Long id);
}
