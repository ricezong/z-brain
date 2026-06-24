package cn.kong.zbrain.service;

import cn.kong.zbrain.dto.request.PromptTemplateRequest;
import cn.kong.zbrain.entity.PromptTemplate;

import java.util.List;

/**
 * 提示词模板服务接口
 *
 * @author zbrain-team
 */
public interface PromptTemplateService {

    Long create(PromptTemplateRequest request);

    void update(Long id, PromptTemplateRequest request);

    void delete(Long id);

    PromptTemplate getById(Long id);

    PromptTemplate getByKbId(Long kbId);

    PromptTemplate getDefault();

    List<PromptTemplate> list(Long kbId);
}
