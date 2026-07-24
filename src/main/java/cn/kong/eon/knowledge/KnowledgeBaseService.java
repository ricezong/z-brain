package cn.kong.eon.knowledge;

import cn.kong.eon.persistence.entity.KnowledgeBase;
import cn.kong.eon.persistence.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库管理服务（重写：去 ServiceImpl 后缀，直接领域命名）
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public List<KnowledgeBase> list() {
        return knowledgeBaseMapper.selectList(null, null, null, 0, 1000);
    }

    public KnowledgeBase getById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    public void create(KnowledgeBase kb) {
        knowledgeBaseMapper.insert(kb);
    }

    public void update(KnowledgeBase kb) {
        knowledgeBaseMapper.update(kb);
    }

    public void delete(Long id) {
        knowledgeBaseMapper.deleteById(id);
    }
}
