package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.entity.SysPrompt;
import cn.kong.zbrain.mapper.SysPromptMapper;
import cn.kong.zbrain.service.SysPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统提示词服务实现
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysPromptServiceImpl implements SysPromptService {

    private final SysPromptMapper sysPromptMapper;

    @Override
    public String getContent(String promptKey) {
        SysPrompt prompt = sysPromptMapper.selectByKey(promptKey);
        if (prompt == null) {
            log.warn("系统提示词未找到: key={}", promptKey);
            return null;
        }
        return prompt.getContent();
    }

    @Override
    public SysPrompt getByKey(String promptKey) {
        SysPrompt prompt = sysPromptMapper.selectByKey(promptKey);
        if (prompt == null) {
            throw new BusinessException(404, "系统提示词不存在: " + promptKey);
        }
        return prompt;
    }

    @Override
    public List<SysPrompt> list() {
        return sysPromptMapper.selectList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, String description, String content, Boolean isActive) {
        SysPrompt existing = getById(id);
        SysPrompt prompt = new SysPrompt();
        prompt.setId(id);
        prompt.setName(name);
        prompt.setDescription(description);
        prompt.setContent(content);
        prompt.setIsActive(isActive);
        sysPromptMapper.update(prompt);
        log.info("更新系统提示词: id={}, key={}", id, existing.getPromptKey());
    }

    @Override
    public SysPrompt getById(Long id) {
        SysPrompt prompt = sysPromptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException(404, "系统提示词不存在");
        }
        return prompt;
    }
}
