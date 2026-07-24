package cn.kong.eon.knowledge;

import cn.kong.eon.persistence.entity.Chunk;
import cn.kong.eon.persistence.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分块管理服务（CRUD + 审核）
 *
 * @author eon-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ChunkMapper chunkMapper;

    public List<Chunk> listByDocId(Long docId) {
        return chunkMapper.selectByDocId(docId);
    }

    public List<Chunk> listChildChunks(Long docId, String status) {
        return chunkMapper.selectChildChunksByDocId(docId, status);
    }

    public Chunk getById(Long id) {
        return chunkMapper.selectById(id);
    }

    public void updateContent(Long id, String content, Integer tokenCount) {
        chunkMapper.updateContent(id, content, tokenCount);
    }

    public void updateStatus(Long id, String status) {
        chunkMapper.updateStatus(id, status);
    }

    public void batchUpdateStatus(List<Long> ids, String status) {
        chunkMapper.batchUpdateStatus(ids, status);
    }

    public void merge(Long parentId, List<Long> childIds) {
        for (Long childId : childIds) {
            chunkMapper.updateParentId(childId, parentId);
        }
    }

    public void split(Long chunkId, String content, Integer tokenCount) {
        chunkMapper.updateContent(chunkId, content, tokenCount);
    }

    public void deleteByDocId(Long docId) {
        chunkMapper.deleteByDocId(docId);
    }
}
