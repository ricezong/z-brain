package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.dto.request.PromptTemplateRequest;
import cn.kong.zbrain.entity.PromptTemplate;
import cn.kong.zbrain.mapper.PromptTemplateMapper;
import cn.kong.zbrain.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词模板服务实现
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PromptTemplateRequest request) {
        PromptTemplate template = new PromptTemplate();
        template.setKbId(request.getKbId());
        template.setName(request.getName());
        template.setSystemPrompt(request.getSystemPrompt());
        template.setUserPrompt(request.getUserPrompt());
        template.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        promptTemplateMapper.insert(template);
        log.info("创建提示词模板: id={}, name={}", template.getId(), template.getName());
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PromptTemplateRequest request) {
        PromptTemplate existing = getById(id);
        PromptTemplate template = new PromptTemplate();
        template.setId(id);
        template.setName(request.getName());
        template.setSystemPrompt(request.getSystemPrompt());
        template.setUserPrompt(request.getUserPrompt());
        template.setIsDefault(request.getIsDefault());
        promptTemplateMapper.update(template);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        promptTemplateMapper.deleteById(id);
    }

    @Override
    public PromptTemplate getById(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "提示词模板不存在");
        }
        return template;
    }

    @Override
    public PromptTemplate getByKbId(Long kbId) {
        PromptTemplate template = promptTemplateMapper.selectByKbId(kbId);
        if (template == null) {
            template = getDefault();
        }
        return template;
    }

    @Override
    public PromptTemplate getDefault() {
        PromptTemplate template = promptTemplateMapper.selectDefault();
        if (template == null) {
            throw new BusinessException("未找到默认提示词模板，请初始化数据库");
        }
        return template;
    }

    @Override
    public List<PromptTemplate> list(Long kbId) {
        return promptTemplateMapper.selectList(kbId);
    }
}
