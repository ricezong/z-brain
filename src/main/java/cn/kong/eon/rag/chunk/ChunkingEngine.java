package cn.kong.eon.rag.chunk;

import cn.kong.eon.common.util.TokenUtils;
import cn.kong.eon.config.EonProperties;
import cn.kong.eon.persistence.entity.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块引擎（重写：去 ChunkingEngine 接口/impl 分层，直接领域命名）
 *
 * <p>双层递归字符分块：父层粗切（~1000 token）保留完整语义上下文，
 * 子层细切（~300 token）用于精确向量检索。父子关系通过 parent_id 关联。</p>
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingEngine {

    private final EonProperties properties;

    /**
     * 执行双层分块
     *
     * @param docId    文档 ID
     * @param kbId     知识库 ID
     * @param content  原始文本
     * @param chunkSize 子块 token 大小（null 时使用知识库配置）
     * @return 分块列表（含父块和子块，parent_id 已关联）
     */
    public List<Chunk> chunk(Long docId, Long kbId, String content, Integer chunkSize) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        EonProperties.Chunk cfg = properties.getChunk();
        int childSize = chunkSize != null ? chunkSize : cfg.getChildTokenSize();
        int childOverlap = cfg.getChildOverlap();
        int parentSize = cfg.getParentTokenSize();
        int parentOverlap = cfg.getParentOverlap();

        List<Chunk> result = new ArrayList<>();

        // 1. 父层切分
        List<String> parentTexts = RecursiveCharacterSplitter.split(content, parentSize, parentOverlap);
        log.info("[ChunkingEngine] 父层切分: {} 块 (parentSize={})", parentTexts.size(), parentSize);

        long parentSeq = 0;
        for (String parentText : parentTexts) {
            // 创建父块
            Chunk parent = new Chunk();
            parent.setDocId(docId);
            parent.setKbId(kbId);
            parent.setChunkType("parent");
            parent.setContent(parentText);
            parent.setTokenCount(TokenUtils.countTokens(parentText));
            parent.setStatus("draft");
            parent.setMetadata("{}");
            // parentSeq 用作临时 ID 占位，batchInsert 后由 DB 生成真实 ID
            parentSeq++;
            result.add(parent);

            // 2. 子层切分
            List<String> childTexts = RecursiveCharacterSplitter.split(parentText, childSize, childOverlap);
            for (String childText : childTexts) {
                Chunk child = new Chunk();
                child.setDocId(docId);
                child.setKbId(kbId);
                child.setChunkType("child");
                child.setContent(childText);
                child.setTokenCount(TokenUtils.countTokens(childText));
                child.setStatus("draft");
                child.setMetadata("{}");
                // parent_id 在 batchInsert 后回填（parent 获得真实 ID 后）
                result.add(child);
            }
        }

        log.info("[ChunkingEngine] 双层分块完成: 父块={}, 总块={}", parentTexts.size(), result.size());
        return result;
    }

    /**
     * 回填子块的 parent_id（batchInsert 后调用）
     *
     * @param chunks 分块列表（父块已有真实 ID）
     */
    public void linkParentIds(List<Chunk> chunks) {
        Long currentParentId = null;
        for (Chunk chunk : chunks) {
            if ("parent".equals(chunk.getChunkType())) {
                currentParentId = chunk.getId();
            } else if ("child".equals(chunk.getChunkType()) && currentParentId != null) {
                chunk.setParentId(currentParentId);
            }
        }
    }
}
