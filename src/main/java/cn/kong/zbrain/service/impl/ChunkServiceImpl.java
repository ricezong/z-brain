package cn.kong.zbrain.service.impl;

import cn.kong.zbrain.chunk.ChunkingEngine;
import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.dto.request.ChunkMergeRequest;
import cn.kong.zbrain.dto.request.ChunkSplitRequest;
import cn.kong.zbrain.dto.request.ChunkUpdateRequest;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.service.ChunkService;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分块服务实现
 *
 * <p>支持人工审核工作台的分块合并、拆分、修改、删除等干预操作。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final ChunkMapper chunkMapper;
    private final ChunkingEngine chunkingEngine;

    @Override
    public List<Chunk> listByDocId(Long docId) {
        return chunkMapper.selectByDocId(docId);
    }

    @Override
    public Chunk getById(Long id) {
        Chunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BusinessException(404, "分块不存在");
        }
        return chunk;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ChunkUpdateRequest request) {
        Chunk existing = getById(request.getId());
        Chunk chunk = new Chunk();
        chunk.setId(request.getId());
        if (request.getContent() != null) {
            chunk.setContent(request.getContent());
            chunk.setTokenCount(TokenUtils.countTokens(request.getContent()));
        }
        if (request.getParentId() != null) {
            chunk.setParentId(request.getParentId());
        }
        if (request.getStatus() != null) {
            chunk.setStatus(request.getStatus());
        }
        if (request.getMetadata() != null) {
            chunk.setMetadata(request.getMetadata());
        }
        chunkMapper.update(chunk);
        log.info("更新分块: id={}", request.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Chunk chunk = getById(id);
        // 如果是父块，同时删除其所有子块
        if ("parent".equals(chunk.getChunkType())) {
            List<Chunk> children = chunkMapper.selectByDocId(chunk.getDocId()).stream()
                    .filter(c -> id.equals(c.getParentId()))
                    .toList();
            for (Chunk child : children) {
                chunkMapper.deleteById(child.getId());
            }
        }
        chunkMapper.deleteById(id);
        log.info("删除分块: id={}, 同时删除子块数={}", id,
                "parent".equals(chunk.getChunkType()) ? "all" : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Chunk merge(ChunkMergeRequest request) {
        List<Chunk> chunks = new ArrayList<>();
        for (Long id : request.getChunkIds()) {
            chunks.add(getById(id));
        }
        // 按创建时间排序
        chunks.sort((a, b) -> {
            if (a.getCreateTime() == null || b.getCreateTime() == null) return 0;
            return a.getCreateTime().compareTo(b.getCreateTime());
        });

        // 拼接内容
        String mergedContent = chunkingEngine.merge(chunks);

        // 创建新分块
        Chunk newChunk = new Chunk();
        newChunk.setDocId(chunks.get(0).getDocId());
        newChunk.setKbId(chunks.get(0).getKbId());
        newChunk.setParentId(request.getParentId() != null ? request.getParentId() : chunks.get(0).getParentId());
        newChunk.setChunkType("child");
        newChunk.setContent(mergedContent);
        newChunk.setTokenCount(TokenUtils.countTokens(mergedContent));
        newChunk.setStatus(chunks.get(0).getStatus());
        newChunk.setMetadata(chunks.get(0).getMetadata());

        chunkMapper.insert(newChunk);

        // 删除原分块
        for (Long id : request.getChunkIds()) {
            chunkMapper.deleteById(id);
        }

        log.info("合并分块: 原IDs={}, 新ID={}", request.getChunkIds(), newChunk.getId());
        return newChunk;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Chunk> split(ChunkSplitRequest request) {
        Chunk original = getById(request.getChunkId());
        if (request.getSplitPosition() <= 0
                || request.getSplitPosition() >= original.getContent().length()) {
            throw new BusinessException("拆分位置超出范围");
        }

        List<Chunk> splitChunks = chunkingEngine.split(original, request.getSplitPosition());
        // 插入新分块
        for (Chunk chunk : splitChunks) {
            chunkMapper.insert(chunk);
        }
        // 删除原分块
        chunkMapper.deleteById(original.getId());

        log.info("拆分分块: 原ID={}, 新IDs={}", original.getId(),
                splitChunks.stream().map(Chunk::getId).map(String::valueOf).collect(Collectors.joining(",")));
        return splitChunks;
    }

    @Override
    public void adjustParent(Long chunkId, Long parentId) {
        Chunk parent = getById(parentId);
        if (!"parent".equals(parent.getChunkType())) {
            throw new BusinessException("目标分块不是父块");
        }
        chunkMapper.updateParentId(chunkId, parentId);
        log.info("调整父子关系: chunkId={}, parentId={}", chunkId, parentId);
    }
}
