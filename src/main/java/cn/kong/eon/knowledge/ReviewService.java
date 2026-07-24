package cn.kong.eon.knowledge;

import cn.kong.eon.persistence.entity.Chunk;
import cn.kong.eon.persistence.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分块审核工作台服务
 *
 * <p>文档解析分块后进入 pending_review 状态，审核通过后激活（status=active）并触发向量化。
 * 审核操作包括：</p>
 * <ul>
 *   <li>查看待审核分块</li>
 *   <li>修改分块内容（审核 Diff，修正解析错误）</li>
 *   <li>合并/拆分分块</li>
 *   <li>批量通过审核 → 触发 embedding</li>
 * </ul>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ChunkMapper chunkMapper;
    private final ChunkService chunkService;
    private final EmbeddingTaskService embeddingTaskService;

    /**
     * 获取待审核分块列表
     */
    public List<Chunk> listPending(Long docId) {
        return chunkService.listChildChunks(docId, "draft");
    }

    /**
     * 审核修改分块内容（content 变更自动失效向量，触发器回退 status=draft）
     */
    public void reviewUpdate(Long chunkId, String content, Integer tokenCount) {
        chunkService.updateContent(chunkId, content, tokenCount);
        log.info("[ReviewService] 分块内容已修改: chunkId={}", chunkId);
    }

    /**
     * 合并分块（将多个子块关联到同一父块）
     */
    @Transactional
    public void merge(Long parentId, List<Long> childIds) {
        chunkService.merge(parentId, childIds);
        log.info("[ReviewService] 分块合并: parentId={}, childIds={}", parentId, childIds);
    }

    /**
     * 拆分分块（修改内容后重新分块由上层处理）
     */
    public void split(Long chunkId, String content, Integer tokenCount) {
        chunkService.split(chunkId, content, tokenCount);
        log.info("[ReviewService] 分块拆分: chunkId={}", chunkId);
    }

    /**
     * 批量审核通过 → 激活分块 → 触发异步 embedding
     */
    @Transactional
    public void approveBatch(Long docId, List<Long> chunkIds) {
        // 激活选中的分块
        if (chunkIds != null && !chunkIds.isEmpty()) {
            chunkService.batchUpdateStatus(chunkIds, "active");
        }
        // 激活文档下所有 draft 分块（全量通过时 chunkIds 为 null）
        if (chunkIds == null) {
            List<Chunk> drafts = chunkService.listChildChunks(docId, "draft");
            for (Chunk c : drafts) {
                chunkService.updateStatus(c.getId(), "active");
            }
        }
        log.info("[ReviewService] 审核通过: docId={}, chunks={}", docId,
                chunkIds != null ? chunkIds.size() : "all");

        // 触发异步 embedding
        embeddingTaskService.embedDocument(docId);
    }

    /**
     * 审核驳回（删除分块，文档状态回退）
     */
    @Transactional
    public void reject(Long docId, String reason) {
        log.warn("[ReviewService] 审核驳回: docId={}, reason={}", docId, reason);
        // 分块保留但状态保持 draft，等待重新审核
    }
}
