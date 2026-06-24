package cn.kong.zbrain.service;

import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.dto.request.KnowledgeBaseCreateRequest;
import cn.kong.zbrain.dto.request.KnowledgeBaseUpdateRequest;
import cn.kong.zbrain.entity.KnowledgeBase;

import java.util.List;

/**
 * 知识库服务接口
 *
 * @author zbrain-team
 */
public interface KnowledgeBaseService {

    Long create(KnowledgeBaseCreateRequest request);

    void update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);

    KnowledgeBase getById(Long id);

    PageResult<KnowledgeBase> list(String name, String category, String status, int pageNum, int pageSize);

    List<String> listCategories();
}
