package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.entity.SysLlmModel;
import cn.kong.zbrain.mapper.SysLlmModelMapper;
import cn.kong.zbrain.service.SysLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LLM 模型配置服务实现
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLlmModelServiceImpl implements SysLlmModelService {

    private final SysLlmModelMapper sysLlmModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysLlmModel model) {
        // 如果设为默认，先清除同类型的其他默认
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            sysLlmModelMapper.clearDefaultByType(model.getModelType());
        }
        sysLlmModelMapper.insert(model);
        log.info("创建 LLM 模型配置: id={}, name={}, type={}", model.getId(), model.getName(), model.getModelType());
        return model.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysLlmModel model) {
        SysLlmModel existing = getById(model.getId());
        // 如果设为默认，先清除同类型的其他默认
        if (Boolean.TRUE.equals(model.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            sysLlmModelMapper.clearDefaultByType(existing.getModelType());
        }
        sysLlmModelMapper.update(model);
        log.info("更新 LLM 模型配置: id={}, name={}", model.getId(), model.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysLlmModel existing = getById(id);
        // 如果删除的是默认模型，发出警告
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            log.warn("删除的模型是默认模型，删除后该类型无默认模型: id={}, type={}", id, existing.getModelType());
        }
        sysLlmModelMapper.deleteById(id);
        log.info("删除 LLM 模型配置: id={}, name={}", id, existing.getName());
    }

    @Override
    public SysLlmModel getById(Long id) {
        SysLlmModel model = sysLlmModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(404, "LLM 模型配置不存在");
        }
        return model;
    }

    @Override
    public List<SysLlmModel> listAll() {
        return sysLlmModelMapper.selectAll();
    }

    @Override
    public List<SysLlmModel> listByType(String modelType) {
        return sysLlmModelMapper.selectByType(modelType);
    }

    @Override
    public SysLlmModel getDefaultByType(String modelType) {
        return sysLlmModelMapper.selectDefaultByType(modelType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        SysLlmModel model = getById(id);
        sysLlmModelMapper.clearDefaultByType(model.getModelType());
        SysLlmModel update = new SysLlmModel();
        update.setId(id);
        update.setIsDefault(true);
        sysLlmModelMapper.update(update);
        log.info("设置默认模型: id={}, type={}", id, model.getModelType());
    }
}
