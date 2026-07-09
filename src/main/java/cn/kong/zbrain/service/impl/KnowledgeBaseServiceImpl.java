package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.common.PageResult;
import cn.kong.zbrain.dto.request.KnowledgeBaseCreateRequest;
import cn.kong.zbrain.dto.request.KnowledgeBaseUpdateRequest;
import cn.kong.zbrain.entity.KnowledgeBase;
import cn.kong.zbrain.mapper.KnowledgeBaseMapper;
import cn.kong.zbrain.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 知识库服务实现
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeBaseCreateRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory() : "general");
        kb.setPromptTemplateId(request.getPromptTemplateId());
        kb.setChunkSize(request.getChunkSize() != null ? request.getChunkSize() : 300);
        kb.setStatus("active");
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setCreateBy("system");
        knowledgeBaseMapper.insert(kb);
        log.info("创建知识库成功: id={}, name={}", kb.getId(), kb.getName());
        return kb.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBase existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setCategory(request.getCategory());
        kb.setPromptTemplateId(request.getPromptTemplateId());
        kb.setChunkSize(request.getChunkSize());
        kb.setStatus(request.getStatus());
        kb.setUpdateBy("system");
        knowledgeBaseMapper.update(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBase existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        // 软删除：标记为 archived
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setStatus("archived");
        knowledgeBaseMapper.update(kb);
        log.info("删除知识库（软删除）: id={}", id);
    }

    @Override
    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        return kb;
    }

    @Override
    public PageResult<KnowledgeBase> list(String name, String category, String status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(name, category, status, offset, pageSize);
        long total = knowledgeBaseMapper.countList(name, category, status);
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    @Override
    public List<String> listCategories() {
        return Arrays.asList("general", "product", "technical", "faq", "policy", "legal", "finance");
    }
}
